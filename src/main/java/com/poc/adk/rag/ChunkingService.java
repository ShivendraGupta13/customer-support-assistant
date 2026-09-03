package com.poc.adk.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Header-aware chunking: split on {@code ##} then {@code ###}, cap ~400 tokens (~1,500
 * characters), and apply ~50-token (~200 character) overlap only on size-splits.
 */
public final class ChunkingService {

  static final int MAX_CHARS = 1500;
  static final int OVERLAP_CHARS = 200;

  public List<PolicyChunk> chunk(String markdown, String sourcePath) {
    String docId = docId(sourcePath);
    List<Section> sections = splitSections(markdown);
    List<PolicyChunk> chunks = new ArrayList<>();
    int index = 0;
    for (Section section : sections) {
      List<String> parts = fit(section.body());
      for (String part : parts) {
        chunks.add(new PolicyChunk(docId, sourcePath, section.heading(), index++, part));
      }
    }
    return chunks;
  }

  private static String docId(String sourcePath) {
    String name = sourcePath;
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    if (name.endsWith(".md")) {
      return name.substring(0, name.length() - 3);
    }
    return name;
  }

  private static List<Section> splitSections(String markdown) {
    List<Section> sections = new ArrayList<>();
    String heading = "";
    StringBuilder body = new StringBuilder();
    for (String line : markdown.split("\n", -1)) {
      if (isSectionHeading(line)) {
        flushSection(sections, heading, body);
        heading = line.replaceFirst("^#{2,3}\\s+", "").trim();
        body.setLength(0);
      } else if (line.startsWith("# ")) {
        continue;
      } else {
        if (body.length() > 0) {
          body.append('\n');
        }
        body.append(line);
      }
    }
    flushSection(sections, heading, body);
    return sections;
  }

  private static boolean isSectionHeading(String line) {
    return line.startsWith("## ") || line.startsWith("### ");
  }

  private static void flushSection(List<Section> sections, String heading, StringBuilder body) {
    String text = body.toString().trim();
    if (heading.isEmpty() && text.isEmpty()) {
      return;
    }
    sections.add(new Section(heading.isEmpty() ? "Preamble" : heading, text));
  }

  private static List<String> fit(String text) {
    if (text.length() <= MAX_CHARS) {
      return text.isEmpty() ? List.of() : List.of(text);
    }
    List<String> pieces = splitByParagraphsThenSentences(text);
    return packWithOverlap(pieces);
  }

  private static List<String> splitByParagraphsThenSentences(String text) {
    List<String> parts = new ArrayList<>();
    for (String paragraph : text.split("\\n\\n+")) {
      String trimmed = paragraph.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.length() <= MAX_CHARS) {
        parts.add(trimmed);
      } else {
        parts.addAll(splitSentences(trimmed));
      }
    }
    return parts;
  }

  private static List<String> splitSentences(String text) {
    String[] sentences = text.split("(?<=[.!?])\\s+");
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String sentence : sentences) {
      if (current.length() > 0 && current.length() + 1 + sentence.length() > MAX_CHARS) {
        parts.add(current.toString());
        current.setLength(0);
      }
      if (sentence.length() > MAX_CHARS) {
        if (current.length() > 0) {
          parts.add(current.toString());
          current.setLength(0);
        }
        parts.addAll(hardSplit(sentence));
      } else if (current.length() == 0) {
        current.append(sentence);
      } else {
        current.append(' ').append(sentence);
      }
    }
    if (current.length() > 0) {
      parts.add(current.toString());
    }
    return parts;
  }

  private static List<String> hardSplit(String text) {
    List<String> parts = new ArrayList<>();
    int i = 0;
    while (i < text.length()) {
      int end = Math.min(i + MAX_CHARS, text.length());
      parts.add(text.substring(i, end));
      i = end;
    }
    return parts;
  }

  private static List<String> packWithOverlap(List<String> pieces) {
    List<String> packed = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String piece : pieces) {
      if (current.length() == 0) {
        current.append(piece);
        continue;
      }
      if (current.length() + 2 + piece.length() <= MAX_CHARS) {
        current.append("\n\n").append(piece);
      } else {
        String chunk = current.toString();
        packed.add(chunk);
        current.setLength(0);
        String overlap = overlapOf(chunk);
        if (!overlap.isEmpty()) {
          current.append(overlap);
          if (overlap.length() + 2 + piece.length() <= MAX_CHARS + OVERLAP_CHARS) {
            current.append("\n\n").append(piece);
          } else {
            packed.add(current.toString());
            current.setLength(0);
            current.append(piece);
          }
        } else {
          current.append(piece);
        }
      }
    }
    if (current.length() > 0) {
      packed.add(current.toString());
    }
    return packed;
  }

  private static String overlapOf(String chunk) {
    if (chunk.length() <= OVERLAP_CHARS) {
      return chunk;
    }
    return chunk.substring(chunk.length() - OVERLAP_CHARS);
  }

  private record Section(String heading, String body) {}
}
