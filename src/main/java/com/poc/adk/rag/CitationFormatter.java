package com.poc.adk.rag;

public final class CitationFormatter {

  private CitationFormatter() {}

  public static String format(RetrievedChunk chunk) {
    return "Source: " + chunk.sourcePath() + " — " + chunk.sectionHeading();
  }
}
