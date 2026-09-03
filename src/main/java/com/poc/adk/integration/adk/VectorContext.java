package com.poc.adk.integration.adk;

import io.qdrant.client.QdrantClient;

/**
 * Static bridge so non-Spring agent classes can reach the configured {@link QdrantClient}.
 * Populated by {@code QdrantConfig} during context refresh.
 */
public final class VectorContext {

  private static volatile QdrantClient qdrantClient;

  private VectorContext() {}

  public static void initialize(QdrantClient client) {
    qdrantClient = client;
  }

  public static void reset() {
    qdrantClient = null;
  }

  public static QdrantClient qdrantClient() {
    QdrantClient current = qdrantClient;
    if (current == null) {
      throw new IllegalStateException("VectorContext not initialized");
    }
    return current;
  }
}
