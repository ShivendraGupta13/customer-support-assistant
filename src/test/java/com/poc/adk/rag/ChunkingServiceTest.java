package com.poc.adk.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

  private final ChunkingService chunking = new ChunkingService();

  @Test
  void splitsOnHashHeadingsAndRecordsCitationMetadata() {
    String markdown =
        """
        # Title

        ## Refund request window

        Customers have 30 days.

        ### Opened items

        Opened software is ineligible.

        ## Store credit

        Store credit may be offered.
        """;

    List<PolicyChunk> chunks = chunking.chunk(markdown, "refund-policy.md");

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).docId()).isEqualTo("refund-policy");
    assertThat(chunks.get(0).sourcePath()).isEqualTo("refund-policy.md");
    assertThat(chunks.get(0).sectionHeading()).isEqualTo("Refund request window");
    assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
    assertThat(chunks.get(0).text()).contains("30 days").doesNotContain("Opened software");

    assertThat(chunks.get(1).sectionHeading()).isEqualTo("Opened items");
    assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
    assertThat(chunks.get(1).text()).contains("Opened software");

    assertThat(chunks.get(2).sectionHeading()).isEqualTo("Store credit");
    assertThat(chunks.get(2).chunkIndex()).isEqualTo(2);
  }

  @Test
  void sizeSplitsOversizedSectionWithOverlap() {
    String paragraph1 = "First paragraph. ".repeat(40);
    String paragraph2 = "Second paragraph. ".repeat(40);
    String paragraph3 = "Third paragraph. ".repeat(40);
    String markdown = "## Oversized\n\n" + paragraph1 + "\n\n" + paragraph2 + "\n\n" + paragraph3;

    List<PolicyChunk> chunks = chunking.chunk(markdown, "big.md");

    assertThat(chunks.size()).isGreaterThan(1);
    assertThat(chunks).allMatch(c -> c.sectionHeading().equals("Oversized"));
    assertThat(chunks).allMatch(c -> c.text().length() <= ChunkingService.MAX_CHARS + ChunkingService.OVERLAP_CHARS);
    assertThat(chunks.get(1).text()).contains(chunks.get(0).text().substring(chunks.get(0).text().length() - 50));
    for (int i = 0; i < chunks.size(); i++) {
      assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
    }
  }

  @Test
  void plantedHybridTokensAppearOnlyInLoyaltyPolicyChunks() throws Exception {
    List<PolicyChunk> refund = chunksFromClasspath("refund-policy.md");
    List<PolicyChunk> shipping = chunksFromClasspath("shipping-policy.md");
    List<PolicyChunk> fraud = chunksFromClasspath("fraud-policy.md");
    List<PolicyChunk> loyalty = chunksFromClasspath("loyalty-policy.md");

    assertThat(joined(refund) + joined(shipping) + joined(fraud))
        .doesNotContain("NW-SHIP-EXC-04")
        .doesNotContain("NW-HP-1001");
    assertThat(joined(loyalty)).contains("NW-SHIP-EXC-04").contains("NW-HP-1001");
    assertThat(shipping.stream().map(PolicyChunk::sectionHeading).toList())
        .anyMatch(h -> h.toLowerCase().contains("weather"));
  }

  private List<PolicyChunk> chunksFromClasspath(String filename) throws Exception {
    try (var in = getClass().getResourceAsStream("/policies/" + filename)) {
      assertThat(in).as(filename).isNotNull();
      String markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return chunking.chunk(markdown, filename);
    }
  }

  private static String joined(List<PolicyChunk> chunks) {
    StringBuilder out = new StringBuilder();
    for (PolicyChunk chunk : chunks) {
      out.append(chunk.text());
    }
    return out.toString();
  }
}
