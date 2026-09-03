package com.poc.adk.agents.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads versioned prompt markdown from the classpath. */
public final class AgentPrompts {

  private AgentPrompts() {}

  /** @param classpathPath path relative to classpath root, e.g. {@code prompts/demo-single-agent.v1.md} */
  public static String load(String classpathPath) {
    String normalized = classpathPath.startsWith("/") ? classpathPath : "/" + classpathPath;
    try (InputStream in = AgentPrompts.class.getResourceAsStream(normalized)) {
      if (in == null) {
        throw new IllegalArgumentException("Prompt not found on classpath: " + normalized);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed reading prompt " + normalized, e);
    }
  }
}
