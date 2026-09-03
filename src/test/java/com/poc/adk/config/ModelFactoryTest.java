package com.poc.adk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.Claude;
import com.google.adk.models.Gemini;
import com.poc.adk.config.ModelRoutingProperties.LlmProvider;
import org.junit.jupiter.api.Test;

class ModelFactoryTest {

  @Test
  void ollama_returnsChatCompletionsLlmWithConfiguredModel() {
    var properties = baseProperties();
    properties.setProvider(LlmProvider.ollama);

    BaseLlm llm = ModelFactory.create(properties);

    assertThat(llm).isInstanceOf(ChatCompletionsLlm.class);
    assertThat(llm.model()).isEqualTo("qwen2.5:7b");
  }

  @Test
  void gemini_returnsGeminiWithConfiguredModel() {
    var properties = baseProperties();
    properties.setProvider(LlmProvider.gemini);
    properties.getGemini().setApiKey("test-gemini-key");

    BaseLlm llm = ModelFactory.create(properties);

    assertThat(llm).isInstanceOf(Gemini.class);
    assertThat(llm.model()).isEqualTo("gemini-2.0-flash");
  }

  @Test
  void anthropic_returnsClaudeWithConfiguredModel() {
    var properties = baseProperties();
    properties.setProvider(LlmProvider.anthropic);
    properties.getAnthropic().setApiKey("test-anthropic-key");

    BaseLlm llm = ModelFactory.create(properties);

    assertThat(llm).isInstanceOf(Claude.class);
    assertThat(llm.model()).isEqualTo("claude-3-7-sonnet-20250219");
  }

  @Test
  void openrouter_returnsChatCompletionsLlmWithConfiguredModel() {
    var properties = baseProperties();
    properties.setProvider(LlmProvider.openrouter);
    properties.getOpenrouter().setApiKey("test-openrouter-key");

    BaseLlm llm = ModelFactory.create(properties);

    assertThat(llm).isInstanceOf(ChatCompletionsLlm.class);
    assertThat(llm.model()).isEqualTo("openrouter/auto");
  }

  @Test
  void gemini_withoutApiKeyFailsFast() {
    var properties = baseProperties();
    properties.setProvider(LlmProvider.gemini);
    properties.getGemini().setApiKey("");

    assertThatThrownBy(() -> ModelFactory.create(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gemini.api-key");
  }

  private static ModelRoutingProperties baseProperties() {
    return new ModelRoutingProperties();
  }
}
