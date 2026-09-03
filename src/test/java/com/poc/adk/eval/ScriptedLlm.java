package com.poc.adk.eval;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic {@link BaseLlm} for Layer 3 graph tests (ADK {@code TestLlm} is test-scoped and
 * not on the runtime classpath).
 */
public final class ScriptedLlm extends BaseLlm {

  private final List<LlmResponse> responses;
  private final AtomicInteger index = new AtomicInteger();
  private final List<LlmRequest> requests = new ArrayList<>();

  public ScriptedLlm(List<LlmResponse> responses) {
    super("scripted-llm");
    this.responses = List.copyOf(responses);
  }

  public static ScriptedLlm of(LlmResponse... responses) {
    return new ScriptedLlm(List.of(responses));
  }

  public static LlmResponse text(String text) {
    return LlmResponse.builder()
        .content(Content.builder().role("model").parts(List.of(Part.fromText(text))).build())
        .build();
  }

  public static LlmResponse functionCall(String name, Map<String, Object> args) {
    return LlmResponse.builder()
        .content(
            Content.builder()
                .role("model")
                .parts(List.of(Part.fromFunctionCall(name, args)))
                .build())
        .build();
  }

  public List<LlmRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
    requests.add(llmRequest);
    int i = index.getAndIncrement();
    if (i >= responses.size()) {
      return Flowable.error(
          new NoSuchElementException(
              "ScriptedLlm out of responses (requested index " + i + ", size " + responses.size() + ")"));
    }
    return Flowable.just(responses.get(i));
  }

  @Override
  public BaseLlmConnection connect(LlmRequest llmRequest) {
    throw new UnsupportedOperationException("ScriptedLlm does not support live connections");
  }
}
