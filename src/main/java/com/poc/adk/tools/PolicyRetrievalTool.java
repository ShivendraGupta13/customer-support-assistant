package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.rag.CitationFormatter;
import com.poc.adk.rag.HybridRetriever;
import com.poc.adk.rag.RetrievedChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RAG tool for the sequential investigation policy_check stage. */
public final class PolicyRetrievalTool {

  private static volatile PolicyRetrievalTool instance;

  private final HybridRetriever retriever;

  public PolicyRetrievalTool(HybridRetriever retriever) {
    this.retriever = retriever;
    instance = this;
  }

  @Schema(
      name = "policy_retrieve",
      description = "Retrieve relevant Northwind policy chunks for a shipping/refund/fraud question")
  public static Map<String, Object> policyRetrieve(
      @Schema(name = "query", description = "Natural-language policy question") String query) {
    List<RetrievedChunk> chunks = current().retriever.retrieveHybrid(query);
    List<Map<String, Object>> formatted = new ArrayList<>(chunks.size());
    for (RetrievedChunk chunk : chunks) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("doc_id", chunk.docId());
      row.put("source_path", chunk.sourcePath());
      row.put("section_heading", chunk.sectionHeading());
      row.put("chunk_index", chunk.chunkIndex());
      row.put("text", chunk.text());
      row.put("citation", CitationFormatter.format(chunk));
      row.put("score", chunk.score());
      formatted.add(row);
    }
    return Map.of("query", query, "chunks", formatted);
  }

  private static PolicyRetrievalTool current() {
    PolicyRetrievalTool current = instance;
    if (current == null) {
      throw new IllegalStateException("PolicyRetrievalTool not initialized");
    }
    return current;
  }
}
