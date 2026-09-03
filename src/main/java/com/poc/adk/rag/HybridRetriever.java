package com.poc.adk.rag;

import static io.qdrant.client.QueryFactory.fusion;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import com.poc.adk.config.QdrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.Fusion;
import io.qdrant.client.grpc.Points.PrefetchQuery;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dense cosine prefetch + BM25 lexical prefetch, fused with RRF in one {@code queryAsync}.
 * Qdrant 1.15 fusion(RRF) uses {@code 1/(1+rank)} ({@code k=1}); swapped ranks therefore tie.
 * Equal scores are ordered by how many query identifiers appear in the chunk text.
 */
public final class HybridRetriever {

  private static final int PREFETCH_LIMIT = 20;
  private static final int RESULT_LIMIT = 10;
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Z]{2,}[-A-Z0-9]*[0-9][A-Z0-9-]*");

  private final QdrantClient qdrant;
  private final EmbeddingClient embeddings;

  public HybridRetriever(QdrantClient qdrant, EmbeddingClient embeddings) {
    this.qdrant = qdrant;
    this.embeddings = embeddings;
  }

  public List<RetrievedChunk> retrieveHybrid(String query) {
    List<Float> dense = toList(embeddings.embed(query));
    QueryPoints request =
        QueryPoints.newBuilder()
            .setCollectionName(QdrantConfig.COLLECTION)
            .addPrefetch(
                PrefetchQuery.newBuilder()
                    .setQuery(nearest(dense))
                    .setUsing(QdrantConfig.DENSE_VECTOR)
                    .setLimit(PREFETCH_LIMIT)
                    .build())
            .addPrefetch(
                PrefetchQuery.newBuilder()
                    .setQuery(
                        nearest(
                            Document.newBuilder().setModel("qdrant/bm25").setText(query).build()))
                    .setUsing(QdrantConfig.LEXICAL_VECTOR)
                    .setLimit(PREFETCH_LIMIT)
                    .build())
            .setQuery(fusion(Fusion.RRF))
            .setLimit(RESULT_LIMIT)
            .setWithPayload(enable(true))
            .build();
    return breakScoreTies(query, map(join(qdrant.queryAsync(request))));
  }

  public List<RetrievedChunk> retrieveDenseOnly(String query) {
    List<Float> dense = toList(embeddings.embed(query));
    QueryPoints request =
        QueryPoints.newBuilder()
            .setCollectionName(QdrantConfig.COLLECTION)
            .setQuery(nearest(dense))
            .setUsing(QdrantConfig.DENSE_VECTOR)
            .setLimit(RESULT_LIMIT)
            .setWithPayload(enable(true))
            .build();
    return map(join(qdrant.queryAsync(request)));
  }

  private static List<RetrievedChunk> breakScoreTies(String query, List<RetrievedChunk> chunks) {
    List<String> identifiers = identifiersIn(query);
    if (identifiers.isEmpty()) {
      return chunks;
    }
    chunks.sort(
        (a, b) -> {
          int byScore = Float.compare(b.score(), a.score());
          if (byScore != 0) {
            return byScore;
          }
          return Integer.compare(identifierHits(b.text(), identifiers), identifierHits(a.text(), identifiers));
        });
    return chunks;
  }

  private static List<String> identifiersIn(String query) {
    Matcher matcher = IDENTIFIER.matcher(query.toUpperCase(Locale.ROOT));
    List<String> ids = new ArrayList<>();
    while (matcher.find()) {
      ids.add(matcher.group());
    }
    return ids;
  }

  private static int identifierHits(String text, List<String> identifiers) {
    String upper = text.toUpperCase(Locale.ROOT);
    int hits = 0;
    for (String id : identifiers) {
      if (upper.contains(id)) {
        hits++;
      }
    }
    return hits;
  }

  private static List<RetrievedChunk> map(List<ScoredPoint> points) {
    List<RetrievedChunk> chunks = new ArrayList<>(points.size());
    for (ScoredPoint point : points) {
      Map<String, Value> payload = point.getPayloadMap();
      chunks.add(
          new RetrievedChunk(
              string(payload, "doc_id"),
              string(payload, "source_path"),
              string(payload, "section_heading"),
              (int) payload.getOrDefault("chunk_index", Value.getDefaultInstance()).getIntegerValue(),
              string(payload, "chunk_text"),
              point.getScore()));
    }
    return chunks;
  }

  private static String string(Map<String, Value> payload, String key) {
    Value value = payload.get(key);
    return value == null ? "" : value.getStringValue();
  }

  private static List<Float> toList(float[] vector) {
    List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }

  private static List<ScoredPoint> join(
      com.google.common.util.concurrent.ListenableFuture<List<ScoredPoint>> future) {
    try {
      return future.get(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted querying Qdrant", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Qdrant query failed", e);
    }
  }
}
