package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zellastay.shared.ErrorCategory;
import errorfixtures.DomainFailureController;
import errorfixtures.ProbeErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** How a domain exception becomes a problem response: status, shape, language and secrecy. */
class DomainErrorProblemTest {

  private static final String PROBLEM_JSON = "application/problem+json";

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = ErrorContractMockMvc.around(new DomainFailureController());
  }

  @ParameterizedTest
  @EnumSource(ErrorCategory.class)
  void everyCategoryMapsToItsHttpStatusAndProblemJson(ErrorCategory category) throws Exception {
    int expected = ErrorStatuses.statusOf(category).value();
    String code = new ProbeErrorCode(category).code();

    mvc.perform(get("/probe/domain/" + category.name()))
        .andExpect(status().is(expected))
        .andExpect(
            result -> assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(expected))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.type").value("urn:zellastay:error:" + code))
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
  void errorsHaveStableJsonShape() throws Exception {
    List<String> fields = List.of("type", "title", "status", "detail", "code", "requestId");

    ResultActions result = mvc.perform(get("/probe/not-found"));
    for (String field : fields) {
      result.andExpect(jsonPath("$." + field).exists());
    }
  }
}
