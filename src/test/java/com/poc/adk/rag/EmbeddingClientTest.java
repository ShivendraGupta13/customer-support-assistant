package com.poc.adk.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EmbeddingClientTest {

  @Test
  void postsToOllamaEmbedAndReturns768DimVector() throws Exception {
    float[] expected = new float[768];
    Arrays.fill(expected, 0.1f);
    HttpServer server = serveEmbedJson(embedResponse(expected), new CapturedRequest());

    try {
      EmbeddingClient client =
          new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "nomic-embed-text", 768);
      float[] vector = client.embed("refund request window");

      assertThat(vector).hasSize(768);
      assertThat(vector[0]).isEqualTo(0.1f);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failsFastWhenFirstVectorLengthIsNot768() throws Exception {
    float[] shortVector = new float[] {0.1f, 0.2f, 0.3f};
    HttpServer server = serveEmbedJson(embedResponse(shortVector), new CapturedRequest());

    try {
      EmbeddingClient client =
          new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "nomic-embed-text", 768);
      assertThatThrownBy(() -> client.embed("hello"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("768")
          .hasMessageContaining("3");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void sendsModelAndInputToApiEmbed() throws Exception {
    float[] vector = new float[768];
    CapturedRequest captured = new CapturedRequest();
    HttpServer server = serveEmbedJson(embedResponse(vector), captured);

    try {
      EmbeddingClient client =
          new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "nomic-embed-text", 768);
      client.embed("Does exception code apply?");

      assertThat(captured.method).isEqualTo("POST");
      assertThat(captured.path).isEqualTo("/api/embed");
      assertThat(captured.body).contains("\"model\":\"nomic-embed-text\"");
      assertThat(captured.body).contains("\"input\":\"Does exception code apply?\"");
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer serveEmbedJson(String json, CapturedRequest captured) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/embed",
        exchange -> {
          captured.method = exchange.getRequestMethod();
          captured.path = exchange.getRequestURI().getPath();
          captured.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          byte[] response = json.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static String embedResponse(float[] vector) {
    StringBuilder values = new StringBuilder();
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        values.append(',');
      }
      values.append(vector[i]);
    }
    return "{\"embeddings\":[[" + values + "]]}";
  }

  private static final class CapturedRequest {
    volatile String method;
    volatile String path;
    volatile String body;
  }
}
