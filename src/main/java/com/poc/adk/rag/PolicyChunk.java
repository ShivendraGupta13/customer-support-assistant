package com.poc.adk.rag;

public record PolicyChunk(
    String docId,
    String sourcePath,
    String sectionHeading,
    int chunkIndex,
    String text) {}
