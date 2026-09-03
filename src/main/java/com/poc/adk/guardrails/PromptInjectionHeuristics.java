package com.poc.adk.guardrails;

import java.util.Optional;
import java.util.regex.Pattern;

/** Deterministic prompt-injection / jailbreak flags. No LLM judge. */
public final class PromptInjectionHeuristics {

  private static final Pattern JAILBREAK =
      Pattern.compile(
          "you are now\\s+dan\\b|\\bdo anything now\\b|\\bjailbreak\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern INJECTION =
      Pattern.compile(
          "ignore\\s+(your\\s+)?(previous|prior|all)\\s+instructions|reveal\\s+(your\\s+)?system\\s+prompt",
          Pattern.CASE_INSENSITIVE);

  private PromptInjectionHeuristics() {}

  public static Optional<String> detect(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    if (JAILBREAK.matcher(text).find()) {
      return Optional.of("JAILBREAK");
    }
    if (INJECTION.matcher(text).find()) {
      return Optional.of("PROMPT_INJECTION");
    }
    return Optional.empty();
  }
}
