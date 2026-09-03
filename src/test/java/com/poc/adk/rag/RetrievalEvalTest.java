package com.poc.adk.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.poc.adk.config.QdrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalEvalTest {

  static final String HYBRID_QUERY =
      "Does exception code NW-SHIP-EXC-04 apply to SKU NW-HP-1001?";

  @Test
  void hybridTop1IsLoyaltyCourtesyChunk_denseOnlyIsNot() throws Exception {
    EmbeddingClient embeddings =
        new EmbeddingClient("http://localhost:11434", "nomic-embed-text", 768);
    try (QdrantClient qdrant =
        new QdrantClient(QdrantGrpcClient.newBuilder("localhost", 6334, false).build())) {
      QdrantConfig.recreateCollection(qdrant);
      new PolicyChunkIndexer(qdrant, new ChunkingService(), embeddings).indexFromClasspath();
      HybridRetriever retriever = new HybridRetriever(qdrant, embeddings);

      List<RetrievedChunk> hybrid = retriever.retrieveHybrid(HYBRID_QUERY);
      List<RetrievedChunk> dense = retriever.retrieveDenseOnly(HYBRID_QUERY);
      RetrievedChunk hybridTop = hybrid.getFirst();
      RetrievedChunk denseTop = dense.getFirst();

      assertThat(hybridTop.sourcePath())
          .as("hybrid ranking: %s", brief(hybrid))
          .isEqualTo("loyalty-policy.md");
      assertThat(hybridTop.sectionHeading()).containsIgnoringCase("courtesy");
      assertThat(hybridTop.text()).contains("NW-SHIP-EXC-04").contains("NW-HP-1001");
      assertThat(CitationFormatter.format(hybridTop))
          .isEqualTo("Source: loyalty-policy.md — " + hybridTop.sectionHeading());

      assertThat(denseTop.sourcePath())
          .as("dense ranking: %s", brief(dense))
          .isNotEqualTo("loyalty-policy.md");
    }
  }

  private static String brief(List<RetrievedChunk> chunks) {
    StringBuilder out = new StringBuilder();
    for (RetrievedChunk chunk : chunks) {
      if (out.length() > 0) {
        out.append(" | ");
      }
      out.append(chunk.sourcePath())
          .append(" [")
          .append(chunk.sectionHeading())
          .append("] ")
          .append(chunk.score());
    }
    return out.toString();
  }
}
