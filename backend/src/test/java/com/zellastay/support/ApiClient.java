package com.zellastay.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal JSON-over-HTTP client for integration tests against the random server port. */
public final class ApiClient {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private static final java.util.concurrent.atomic.AtomicInteger NEXT_ADDRESS =
      new java.util.concurrent.atomic.AtomicInteger(1);

  private final String baseUrl;
  private final String clientAddress;

  /** A client that appears to come from its own address, so tests do not share rate limits. */
  public ApiClient(int port) {
    this(
        port,
        "10.%d.%d.%d"
            .formatted(
                (NEXT_ADDRESS.get() >> 16) & 255,
                (NEXT_ADDRESS.get() >> 8) & 255,
                NEXT_ADDRESS.getAndIncrement() & 255));
  }

  public ApiClient(int port, String clientAddress) {
    this.baseUrl = "http://localhost:" + port;
    this.clientAddress = clientAddress;
  }

  public record Response(int status, String body, HttpResponse<String> raw) {

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String header(String name) {
      return raw.headers().firstValue(name).orElse(null);
    }

    public String code() {
      return json().path("code").asString();
    }
  }

  public Response post(String path, Object body, String... headers) {
    return send("POST", path, body, headers);
  }

  public Response get(String path, String... headers) {
    return send("GET", path, null, headers);
  }

  public Response delete(String path, String... headers) {
    return send("DELETE", path, null, headers);
  }

  public static Map<String, Object> map(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  public static String bearer(String token) {
    return "Bearer " + token;
  }

  private Response send(String method, String path, Object body, String... headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "application/json")
            .header("Accept-Language", "en")
            .header("X-Forwarded-For", clientAddress);
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request.header("Content-Type", "application/json");
      request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    }
    try {
      HttpResponse<String> response =
          HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), response.body(), response);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
