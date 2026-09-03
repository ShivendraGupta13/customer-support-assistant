package com.poc.adk.agents.common;

import com.google.genai.types.GenerateContentConfig;

public final class AgentModels {

  private AgentModels() {}

  /** Eval / demo default: temperature 0 for reproducible tool choice and wording. */
  public static GenerateContentConfig temperatureZero() {
    return GenerateContentConfig.builder().temperature(0f).build();
  }
}
