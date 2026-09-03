package com.poc.adk.guardrails;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.Callbacks.AfterModelCallbackSync;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code afterModelCallback}: replace the model response when it contains card numbers.
 *
 * @see <a href="https://github.com/google/adk-java/blob/v1.9.0/core/src/main/java/com/google/adk/agents/Callbacks.java">ADK 1.9.0 Callbacks</a>
 */
public final class OutputGuardrailCallback implements AfterModelCallbackSync {

  public static final OutputGuardrailCallback INSTANCE = new OutputGuardrailCallback();

  private OutputGuardrailCallback() {}

  @Override
  public Optional<LlmResponse> call(CallbackContext callbackContext, LlmResponse llmResponse) {
    Optional<String> text = firstText(llmResponse);
    if (text.isEmpty() || !PiiMasker.containsPii(text.get())) {
      return Optional.empty();
    }

    if (!llmResponse.partial().orElse(false)) {
      GuardrailAuditService.record(callbackContext.sessionId(), "OUTPUT", "PII_CARD", "MASKED");
    }

    Content original = llmResponse.content().orElseThrow();
    List<Part> parts = new ArrayList<>(original.parts().orElse(List.of()));
    parts.set(0, Part.fromText(PiiMasker.mask(text.get())));
    return Optional.of(
        llmResponse.toBuilder()
            .content(original.toBuilder().parts(parts).build())
            .build());
  }

  private static Optional<String> firstText(LlmResponse response) {
    return response
        .content()
        .flatMap(Content::parts)
        .filter(parts -> !parts.isEmpty())
        .flatMap(parts -> parts.getFirst().text());
  }
}
