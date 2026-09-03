package com.poc.adk.config;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.chat.ChatCompletionsClient;
import com.google.adk.models.chat.ChatCompletionsHttpClient;
import com.google.genai.types.HttpOptions;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Map;

/**
 * Thin {@link BaseLlm} over ADK's {@link ChatCompletionsHttpClient} for OpenAI-compatible
 * endpoints (Ollama, OpenRouter). ADK 1.9.0 does not ship {@code OpenAiCompatibleLlm} (PR #1202
 * still open).
 */
final class ChatCompletionsLlm extends BaseLlm {

  private final ChatCompletionsClient client;

  ChatCompletionsLlm(String modelName, String baseUrl, Map<String, String> headers) {
    super(modelName);
    HttpOptions.Builder options = HttpOptions.builder().baseUrl(baseUrl);
    if (!headers.isEmpty()) {
      options.headers(Map.copyOf(headers));
    }
    this.client = new ChatCompletionsHttpClient(options.build());
  }

  @Override
  public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
    return client.complete(llmRequest, stream);
  }

  @Override
  public BaseLlmConnection connect(LlmRequest llmRequest) {
    throw new UnsupportedOperationException(
        "Streaming connections are not supported for chat completions.");
  }
}
