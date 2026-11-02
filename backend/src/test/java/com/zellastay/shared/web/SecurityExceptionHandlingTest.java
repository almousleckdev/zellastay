package com.zellastay.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Method-security failures raised inside controllers must use the standard problem contract. */
class SecurityExceptionHandlingTest {

  @RestController
  static class ProbeController {
    @GetMapping("/probe/denied")
    String denied() {
      throw new AccessDeniedException("internal detail");
    }

    @GetMapping("/probe/unauthenticated")
    String unauthenticated() {
      throw new BadCredentialsException("internal detail");
    }
  }

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages/errors");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    AcceptHeaderLocaleResolver locales = new AcceptHeaderLocaleResolver();
    locales.setDefaultLocale(Locale.FRENCH);
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ApiExceptionHandler(new ProblemFactory(messages)))
            .addFilters(new RequestIdFilter())
            .setLocaleResolver(locales)
            .build();
  }

  @Test
  void accessDeniedBecomesTheStandardForbiddenProblemWithoutInternals() throws Exception {
    mvc.perform(get("/probe/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("auth.forbidden"))
        .andExpect(content().string(not(containsString("internal detail"))));
  }

  @Test
  void authenticationFailureBecomesTheStandardUnauthenticatedProblem() throws Exception {
    mvc.perform(get("/probe/unauthenticated"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.unauthenticated"))
        .andExpect(content().string(not(containsString("internal detail"))));
  }
}
