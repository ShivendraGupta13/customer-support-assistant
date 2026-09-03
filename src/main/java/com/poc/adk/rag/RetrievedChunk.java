package com.poc.adk.rag;

public record RetrievedChunk(
    String docId,
    String sourcePath,
    String sectionHeading,
    int chunkIndex,
    String text,
    float score) {}
