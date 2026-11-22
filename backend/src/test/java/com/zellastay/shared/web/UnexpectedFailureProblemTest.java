package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import errorfixtures.InfrastructureFailureController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** How unexpected and data-access failures become generic problems that leak nothing. */
class UnexpectedFailureProblemTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = ErrorContractMockMvc.around(new InfrastructureFailureController());
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
