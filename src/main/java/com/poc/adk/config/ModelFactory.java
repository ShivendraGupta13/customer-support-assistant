package com.poc.adk.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Claude;
import com.google.adk.models.Gemini;
import java.util.Map;

/** Builds the configured ADK {@link BaseLlm} from {@code llm.provider}. */
public final class ModelFactory {

  private final ModelRoutingProperties properties;

  public ModelFactory(ModelRoutingProperties properties) {
    this.properties = properties;
  }

  public BaseLlm createLlm() {
    return create(properties);
  }

  public static BaseLlm create(ModelRoutingProperties properties) {
    return switch (properties.getProvider()) {
      case ollama ->
          chatCompletionsLlm(
              properties.getOllama().getModel(),
              properties.getOllama().getBaseUrl(),
              Map.of());
      case openrouter ->
          chatCompletionsLlm(
              properties.getOpenrouter().getModel(),
              properties.getOpenrouter().getBaseUrl(),
              Map.of(
                  "Authorization",
                  "Bearer "
                      + requireKey(properties.getOpenrouter().getApiKey(), "openrouter.api-key")));
      case gemini ->
          Gemini.builder()
              .modelName(properties.getGemini().getModel())
              .apiKey(requireKey(properties.getGemini().getApiKey(), "gemini.api-key"))
              .build();
      case anthropic ->
          new Claude(
              properties.getAnthropic().getModel(),
              AnthropicOkHttpClient.builder()
                  .apiKey(requireKey(properties.getAnthropic().getApiKey(), "anthropic.api-key"))
                  .build());
    };
  }

  private static BaseLlm chatCompletionsLlm(
      String model, String baseUrl, Map<String, String> headers) {
    return new ChatCompletionsLlm(model, baseUrl, headers);
  }

  private static String requireKey(String apiKey, String propertyName) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException(propertyName + " must be set");
    }
    return apiKey;
  }
}
