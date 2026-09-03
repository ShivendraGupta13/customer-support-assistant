package com.poc.adk.guardrails;

import java.util.regex.Pattern;

/** Deterministic masking for payment-card numbers. No LLM. */
public final class PiiMasker {

  private static final Pattern CARD = Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");

  private PiiMasker() {}

  public static boolean containsPii(String text) {
    return text != null && CARD.matcher(text).find();
  }

  public static String mask(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return CARD.matcher(text)
        .replaceAll(
            match -> {
              String digits = match.group().replaceAll("[^0-9]", "");
              return "****-****-****-" + digits.substring(digits.length() - 4);
            });
  }
}
