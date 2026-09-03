package com.poc.adk.rag;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.namedVectors;

import com.poc.adk.config.QdrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.PointStruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Indexes policy markdown into Qdrant {@code policy_chunks}. Never writes H2 domain rows.
 */
public final class PolicyChunkIndexer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PolicyChunkIndexer.class);

  private static final List<String> POLICY_FILES =
      List.of("refund-policy.md", "shipping-policy.md", "fraud-policy.md", "loyalty-policy.md");

  private final QdrantClient qdrant;
  private final ChunkingService chunking;
  private final EmbeddingClient embeddings;

  public PolicyChunkIndexer(
      QdrantClient qdrant, ChunkingService chunking, EmbeddingClient embeddings) {
    this.qdrant = qdrant;
    this.chunking = chunking;
    this.embeddings = embeddings;
  }

  @Override
  public void run(ApplicationArguments args) {
    long existing = count();
    if (existing > 0) {
      log.info("Qdrant collection {} already has {} chunks; skipping index", QdrantConfig.COLLECTION, existing);
      return;
    }
    int indexed = indexFromClasspath();
    log.info("Indexed {} policy chunks into {}", indexed, QdrantConfig.COLLECTION);
  }

  public int indexFromClasspath() {
    List<PointStruct> points = new ArrayList<>();
    long pointId = 1;
    for (String filename : POLICY_FILES) {
      String markdown = readClasspath("/policies/" + filename);
      for (PolicyChunk chunk : chunking.chunk(markdown, filename)) {
        points.add(toPoint(pointId++, chunk));
      }
    }
    try {
      qdrant.upsertAsync(QdrantConfig.COLLECTION, points).get(120, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted upserting policy chunks", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Failed upserting policy chunks", e);
    }
    return points.size();
  }

  private PointStruct toPoint(long pointId, PolicyChunk chunk) {
    List<Float> dense = toList(embeddings.embed(chunk.text()));
    return PointStruct.newBuilder()
        .setId(id(pointId))
        .setVectors(
            namedVectors(
                Map.of(
                    QdrantConfig.DENSE_VECTOR,
                    vector(dense),
                    QdrantConfig.LEXICAL_VECTOR,
                    vector(
                        Document.newBuilder()
                            .setModel("qdrant/bm25")
                            .setText(chunk.text())
                            .build()))))
        .putAllPayload(
            Map.of(
                "doc_id", value(chunk.docId()),
                "source_path", value(chunk.sourcePath()),
                "section_heading", value(chunk.sectionHeading()),
                "chunk_index", value((long) chunk.chunkIndex()),
                "chunk_text", value(chunk.text())))
        .build();
  }

  private long count() {
    try {
      return qdrant.countAsync(QdrantConfig.COLLECTION).get(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted counting policy chunks", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Failed counting policy chunks", e);
    }
  }

  private static String readClasspath(String path) {
    try (InputStream in = PolicyChunkIndexer.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed reading " + path, e);
    }
  }

  private static List<Float> toList(float[] vector) {
    List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }
}
