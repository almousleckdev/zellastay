package com.zellastay.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import errorfixtures.RequestValidationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** How invalid, malformed and misdirected requests become problem responses. */
class RequestValidationProblemTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = ErrorContractMockMvc.around(new RequestValidationController());
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
    mvc.perform(post("/probe/param"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("request.method-not-allowed"));
  }
}
