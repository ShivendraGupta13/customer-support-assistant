package com.poc.adk.config;

import com.poc.adk.integration.adk.VectorContext;
import com.poc.adk.rag.ChunkingService;
import com.poc.adk.rag.EmbeddingClient;
import com.poc.adk.rag.HybridRetriever;
import com.poc.adk.rag.PolicyChunkIndexer;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.Modifier;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Qdrant client, idempotent {@code policy_chunks} bootstrap, and RAG wiring. */
@Configuration
@EnableConfigurationProperties({QdrantProperties.class, EmbeddingProperties.class})
public class QdrantConfig {

  public static final String COLLECTION = "policy_chunks";
  public static final String DENSE_VECTOR = "dense";
  public static final String LEXICAL_VECTOR = "lexical";

  @Bean(destroyMethod = "close")
  QdrantClient qdrantClient(QdrantProperties properties) {
    QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(properties.getHost(), properties.getPort(), properties.isTls())
                .build());
    ensureCollection(client);
    VectorContext.initialize(client);
    return client;
  }

  @Bean
  EmbeddingClient embeddingClient(EmbeddingProperties properties) {
    return new EmbeddingClient(properties.getBaseUrl(), properties.getModel(), properties.getDimension());
  }

  @Bean
  ChunkingService chunkingService() {
    return new ChunkingService();
  }

  @Bean
  HybridRetriever hybridRetriever(QdrantClient qdrantClient, EmbeddingClient embeddingClient) {
    return new HybridRetriever(qdrantClient, embeddingClient);
  }

  @Bean
  PolicyChunkIndexer policyChunkIndexer(
      QdrantClient qdrantClient, ChunkingService chunkingService, EmbeddingClient embeddingClient) {
    return new PolicyChunkIndexer(qdrantClient, chunkingService, embeddingClient);
  }

  public static void ensureCollection(QdrantClient client) {
    try {
      if (!client.collectionExistsAsync(COLLECTION).get(30, TimeUnit.SECONDS)) {
        client.createCollectionAsync(collectionSpec()).get(30, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted ensuring Qdrant collection " + COLLECTION, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Failed ensuring Qdrant collection " + COLLECTION, e);
    }
  }

  public static void recreateCollection(QdrantClient client) {
    try {
      if (client.collectionExistsAsync(COLLECTION).get(30, TimeUnit.SECONDS)) {
        client.deleteCollectionAsync(COLLECTION).get(30, TimeUnit.SECONDS);
      }
      client.createCollectionAsync(collectionSpec()).get(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted recreating Qdrant collection " + COLLECTION, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Failed recreating Qdrant collection " + COLLECTION, e);
    }
  }

  static CreateCollection collectionSpec() {
    return CreateCollection.newBuilder()
        .setCollectionName(COLLECTION)
        .setVectorsConfig(
            VectorsConfig.newBuilder()
                .setParamsMap(
                    VectorParamsMap.newBuilder()
                        .putMap(
                            DENSE_VECTOR,
                            VectorParams.newBuilder().setSize(768).setDistance(Distance.Cosine).build())
                        .build())
                .build())
        .setSparseVectorsConfig(
            SparseVectorConfig.newBuilder()
                .putMap(
                    LEXICAL_VECTOR,
                    SparseVectorParams.newBuilder().setModifier(Modifier.Idf).build())
                .build())
        .build();
  }
}
