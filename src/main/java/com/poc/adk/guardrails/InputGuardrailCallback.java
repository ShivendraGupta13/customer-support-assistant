package com.poc.adk.guardrails;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.Callbacks.BeforeModelCallbackSync;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code beforeModelCallback}: skip the model on injection/jailbreak or inbound card numbers.
 *
 * @see <a href="https://github.com/google/adk-java/blob/v1.9.0/core/src/main/java/com/google/adk/agents/Callbacks.java">ADK 1.9.0 Callbacks</a>
 */
public final class InputGuardrailCallback implements BeforeModelCallbackSync {

  public static final InputGuardrailCallback INSTANCE = new InputGuardrailCallback();

  private static final String INJECTION_REFUSAL =
      "I can't comply with requests that try to override my instructions.";

  private InputGuardrailCallback() {}

  @Override
  public Optional<LlmResponse> call(
      CallbackContext callbackContext, LlmRequest.Builder llmRequestBuilder) {
    String text = userText(callbackContext, llmRequestBuilder);

    Optional<String> injection = PromptInjectionHeuristics.detect(text);
    if (injection.isPresent()) {
      GuardrailAuditService.record(
          callbackContext.sessionId(), "INPUT", injection.get(), "BLOCKED");
      return Optional.of(modelText(INJECTION_REFUSAL));
    }

    if (PiiMasker.containsPii(text)) {
      GuardrailAuditService.record(callbackContext.sessionId(), "INPUT", "PII_CARD", "MASKED");
      return Optional.of(
          modelText("I can't store payment card numbers. " + PiiMasker.mask(text)));
    }

    return Optional.empty();
  }

  private static String userText(CallbackContext context, LlmRequest.Builder request) {
    return context
        .userContent()
        .map(InputGuardrailCallback::textOf)
        .orElseGet(() -> lastUserText(request.build().contents()));
  }

  private static String lastUserText(List<Content> contents) {
    for (int i = contents.size() - 1; i >= 0; i--) {
      Content content = contents.get(i);
      if ("user".equals(content.role().orElse(null))) {
        return textOf(content);
      }
    }
    return "";
  }

  private static String textOf(Content content) {
    return content.parts().orElse(List.of()).stream()
        .map(part -> part.text().orElse(""))
        .collect(Collectors.joining());
  }

  private static LlmResponse modelText(String text) {
    return LlmResponse.builder()
        .content(
            Content.builder().role("model").parts(List.of(Part.fromText(text))).build())
        .build();
  }
}
