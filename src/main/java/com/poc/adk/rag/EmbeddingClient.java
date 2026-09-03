package com.poc.adk.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dense embeddings via Ollama {@code POST /api/embed}. Not routed through ADK {@code BaseLlm}.
 * Asserts the first returned vector matches the expected dimension (768 for nomic-embed-text).
 */
public final class EmbeddingClient {

  private final HttpClient http;
  private final ObjectMapper mapper;
  private final URI embedUri;
  private final String model;
  private final int expectedDimension;

  public EmbeddingClient(String baseUrl, String model, int expectedDimension) {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), baseUrl, model, expectedDimension);
  }

  EmbeddingClient(HttpClient http, String baseUrl, String model, int expectedDimension) {
    this.http = http;
    this.mapper = new ObjectMapper();
    this.embedUri = URI.create(trimTrailingSlash(baseUrl) + "/api/embed");
    this.model = model;
    this.expectedDimension = expectedDimension;
  }

  public float[] embed(String text) {
    try {
      ObjectNode body = mapper.createObjectNode();
      body.put("model", model);
      body.put("input", text);
      HttpRequest request =
          HttpRequest.newBuilder(embedUri)
              .timeout(Duration.ofSeconds(60))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException(
            "Ollama embeddings failed: HTTP " + response.statusCode() + " " + response.body());
      }
      float[] vector = parseFirstEmbedding(response.body());
      assertDimension(vector);
      return vector;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Ollama embeddings request failed", e);
    }
  }

  private float[] parseFirstEmbedding(String json) throws IOException {
    JsonNode embeddings = mapper.readTree(json).path("embeddings");
    if (!embeddings.isArray() || embeddings.isEmpty()) {
      throw new IllegalStateException("Ollama embeddings response missing embeddings[0]");
    }
    JsonNode first = embeddings.get(0);
    float[] vector = new float[first.size()];
    for (int i = 0; i < first.size(); i++) {
      vector[i] = (float) first.get(i).asDouble();
    }
    return vector;
  }

  private void assertDimension(float[] vector) {
    if (vector.length != expectedDimension) {
      throw new IllegalStateException(
          "Expected " + expectedDimension + "-dim embedding, got " + vector.length);
    }
  }

  private static String trimTrailingSlash(String baseUrl) {
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }
}
