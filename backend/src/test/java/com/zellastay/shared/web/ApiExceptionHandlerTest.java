package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;
import com.zellastay.shared.ZellaStayException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

class ApiExceptionHandlerTest {

  private static final String PROBLEM_JSON = "application/problem+json";

  record ProbeCode(ErrorCategory category) implements ErrorCode {
    @Override
    public String code() {
      return "probe." + category.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  static class ProbeException extends ZellaStayException {
    @Serial private static final long serialVersionUID = 1L;

    ProbeException(ErrorCode code) {
      super(code, "internal debug detail 42 table=secrets");
    }
  }

  record Body(@NotBlank String name, @Min(1) int count) {}

  @RestController
  static class ProbeController {
    @GetMapping("/probe/domain/{category}")
    void domain(@PathVariable ErrorCategory category) {
      throw new ProbeException(new ProbeCode(category));
    }

    @GetMapping("/probe/not-found")
    void notFound() {
      throw new ProbeException(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @PostMapping("/probe/validate")
    void validate(@Valid @RequestBody Body body) {}

    @GetMapping("/probe/param")
    void param(@RequestParam int x) {}

    @GetMapping("/probe/boom")
    void boom() {
      throw new IllegalStateException("jdbc:postgresql://admin:hunter2@db/zs failed");
    }

    @GetMapping("/probe/integrity")
    void integrity() {
      throw new DataIntegrityViolationException(
          "duplicate key violates uq_secret_email Detail: Key (email)=(a@b.c) already exists");
    }

    @GetMapping("/probe/optimistic")
    void optimistic() {
      throw new OptimisticLockingFailureException("stale row version 7");
    }

    @GetMapping("/probe/transient")
    void transientFailure() {
      throw new QueryTimeoutException("timeout on select * from secrets");
    }
  }

  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages/errors");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    AcceptHeaderLocaleResolver locales = new AcceptHeaderLocaleResolver();
    locales.setDefaultLocale(Locale.FRENCH);
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ApiExceptionHandler(new ProblemFactory(messages)))
            .addFilters(new RequestIdFilter())
            .setValidator(validator)
            .setLocaleResolver(locales)
            .build();
  }

  @ParameterizedTest
  @EnumSource(ErrorCategory.class)
  void everyCategoryMapsToItsHttpStatusAndProblemJson(ErrorCategory category) throws Exception {
    int expected = ErrorStatuses.statusOf(category).value();

    mvc.perform(get("/probe/domain/" + category.name()))
        .andExpect(status().is(expected))
        .andExpect(
            result -> assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(expected))
        .andExpect(jsonPath("$.code").value(new ProbeCode(category).code()))
        .andExpect(
            jsonPath("$.type").value("urn:zellastay:error:" + new ProbeCode(category).code()))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void messagesAreFrenchByDefaultAndEnglishOnRequest() throws Exception {
    mvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Ressource introuvable"));

    mvc.perform(get("/probe/not-found").header("Accept-Language", "en"))
        .andExpect(jsonPath("$.title").value("Resource not found"));

    mvc.perform(get("/probe/not-found").header("Accept-Language", "de"))
        .andExpect(jsonPath("$.title").value("Ressource introuvable"));
  }

  @Test
  void developerMessagesNeverReachTheClient() throws Exception {
    String body =
        mvc.perform(get("/probe/domain/BUSINESS_RULE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("internal debug detail").doesNotContain("secrets");
  }

  @Test
  void validationFailuresListFieldsWithoutEchoingValues() throws Exception {
    String json = "{\"name\":\"\",\"count\":0}";

    ResultActions result =
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation.failed"))
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("validation.not-blank"))
            .andExpect(jsonPath("$.errors[?(@.field=='count')].code").value("validation.min"));

    assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("rejected");
  }

  @Test
  void malformedBodiesDoNotLeakParserInternals() throws Exception {
    String body =
        mvc.perform(
                post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.malformed"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("Jackson").doesNotContain("JsonParse").doesNotContain("oops");
  }

  @Test
  void typeMismatchAndUnknownRoutesAndWrongMethodsUseStandardCodes() throws Exception {
    mvc.perform(get("/probe/param").param("x", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
    mvc.perform(get("/does/not/exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource.not-found"));
    mvc.perform(post("/probe/boom"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("request.method-not-allowed"));
  }

  @Test
  void unexpectedExceptionsBecomeGenericInternalErrors() throws Exception {
    String body =
        mvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal.error"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("jdbc")
        .doesNotContain("hunter2")
        .doesNotContain("IllegalStateException")
        .doesNotContain("com.zellastay")
        .doesNotContain("stackTrace");
  }

  @Test
  void databaseFailuresAreMappedWithoutLeakingSqlOrData() throws Exception {
    String integrity = problemBody("/probe/integrity", 409, "data.conflict");
    String optimistic = problemBody("/probe/optimistic", 409, "data.concurrent-modification");
    String busy = problemBody("/probe/transient", 503, "service.busy");

    assertThat(integrity + optimistic + busy)
        .doesNotContain("uq_secret_email")
        .doesNotContain("a@b.c")
        .doesNotContain("select")
        .doesNotContain("version 7");
  }

  @Test
  void requestIdsAreEchoedWhenValidAndReplacedWhenUnsafe() throws Exception {
    mvc.perform(get("/probe/not-found").header("X-Request-Id", "client-req-12345"))
        .andExpect(jsonPath("$.requestId").value("client-req-12345"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("X-Request-Id"))
                    .isEqualTo("client-req-12345"));

    String generated =
        mvc.perform(get("/probe/not-found").header("X-Request-Id", "bad id\nwith newline"))
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");
    assertThat(generated).matches("[0-9a-f-]{36}");

    String tooLong = "a".repeat(65);
    assertThat(
            mvc.perform(get("/probe/not-found").header("X-Request-Id", tooLong))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id"))
        .isNotEqualTo(tooLong);
  }

  @Test
  void errorsHaveStableJsonShape() throws Exception {
    List<String> fields = List.of("type", "title", "status", "detail", "code", "requestId");

    ResultActions result = mvc.perform(get("/probe/not-found"));
    for (String field : fields) {
      result.andExpect(jsonPath("$." + field).exists());
    }
  }

  private String problemBody(String path, int expectedStatus, String expectedCode)
      throws Exception {
    return mvc.perform(get(path))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
