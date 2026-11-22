package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import errorfixtures.DomainFailureController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** Which client-supplied request ids are echoed and which are replaced. */
class RequestIdFilterTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = ErrorContractMockMvc.around(new DomainFailureController());
  }

  @Test
  void aValidRequestIdIsEchoedInTheHeaderAndTheProblem() throws Exception {
    mvc.perform(get("/probe/not-found").header("X-Request-Id", "client-req-12345"))
        .andExpect(jsonPath("$.requestId").value("client-req-12345"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("X-Request-Id"))
                    .isEqualTo("client-req-12345"));
  }

  @Test
  void anUnsafeRequestIdIsReplacedByAGeneratedOne() throws Exception {
    String generated =
        mvc.perform(get("/probe/not-found").header("X-Request-Id", "bad id\nwith newline"))
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");

    assertThat(generated).matches("[0-9a-f-]{36}");
  }

  @Test
  void anOverlongRequestIdIsReplaced() throws Exception {
    String tooLong = "a".repeat(65);

    String header =
        mvc.perform(get("/probe/not-found").header("X-Request-Id", tooLong))
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");

    assertThat(header).isNotEqualTo(tooLong);
  }
}
