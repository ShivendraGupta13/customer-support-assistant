package com.poc.adk.agents.common;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.poc.adk.integration.adk.LlmContext;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Defers to {@link LlmContext} at call time so static {@code ROOT_AGENT} graphs can be built before
 * Spring finishes refreshing the {@code BaseLlm} bean.
 */
public final class ContextLlm extends BaseLlm {

  public ContextLlm() {
    super("llm-context");
  }

  @Override
  public String model() {
    return LlmContext.baseLlm().model();
  }

  @Override
  public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
    return LlmContext.baseLlm().generateContent(llmRequest, stream);
  }

  @Override
  public BaseLlmConnection connect(LlmRequest llmRequest) {
    return LlmContext.baseLlm().connect(llmRequest);
  }
}
