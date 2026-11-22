package com.zellastay.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RequestIdLoggingIT {

  @Value("${local.server.port}")
  private int port;

  @Test
  void unknownRoutesReturnProblemJsonAndTheRequestIdReachesStructuredLogs(CapturedOutput output)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/no/such/route"))
            .header("X-Request-Id", "it-request-0001")
            .header("Accept-Language", "en")
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
    assertThat(response.headers().firstValue("X-Request-Id")).contains("it-request-0001");
    assertThat(response.body())
        .contains("\"code\":\"resource.not-found\"")
        .contains("\"requestId\":\"it-request-0001\"")
        .contains("Resource not found")
        .doesNotContain("Whitelabel")
        .doesNotContain("trace")
        .doesNotContain("exception");
    assertThat(output.getAll())
        .contains("\"requestId\":\"it-request-0001\"")
        .contains("Request rejected status=404");
  }
}
