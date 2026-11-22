package com.zellastay.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import errorfixtures.SecurityFailureController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** Method-security failures raised inside controllers must use the standard problem contract. */
class SecurityExceptionHandlingTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = ErrorContractMockMvc.around(new SecurityFailureController());
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
