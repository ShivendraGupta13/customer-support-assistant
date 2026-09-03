package com.poc.adk.integration.adk;

import com.google.adk.models.BaseLlm;
import com.poc.adk.config.ModelFactory;

/**
 * Static bridge so non-Spring agent classes can reach the configured {@link ModelFactory} / {@link
 * BaseLlm}. Populated by {@code LlmIntegrationConfig} during context refresh.
 */
public final class LlmContext {

  private static volatile ModelFactory modelFactory;
  private static volatile BaseLlm baseLlm;

  private LlmContext() {}

  public static void initialize(ModelFactory factory, BaseLlm llm) {
    modelFactory = factory;
    baseLlm = llm;
  }

  public static ModelFactory modelFactory() {
    ModelFactory factory = modelFactory;
    if (factory == null) {
      throw new IllegalStateException("LlmContext not initialized");
    }
    return factory;
  }

  public static BaseLlm baseLlm() {
    BaseLlm llm = baseLlm;
    if (llm == null) {
      throw new IllegalStateException("LlmContext not initialized");
    }
    return llm;
  }
}
