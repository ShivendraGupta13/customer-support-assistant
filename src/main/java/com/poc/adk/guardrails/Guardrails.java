package com.poc.adk.guardrails;

import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import java.util.Map;
import java.util.Optional;

/**
 * Shared {@link LlmAgent.Builder} helper so every demo agent gets the same guardrail callbacks.
 */
public final class Guardrails {

  private Guardrails() {}

  public static LlmAgent.Builder apply(LlmAgent.Builder builder) {
    return builder
        .beforeModelCallbackSync(InputGuardrailCallback.INSTANCE)
        .afterModelCallbackSync(OutputGuardrailCallback.INSTANCE)
        .beforeToolCallbackSync(Guardrails::beforeTool)
        .afterToolCallbackSync(Guardrails::afterTool);
  }

  static Optional<Map<String, Object>> beforeTool(
      InvocationContext invocationContext,
      BaseTool baseTool,
      Map<String, Object> input,
      ToolContext toolContext) {
    return Optional.empty();
  }

  static Optional<Map<String, Object>> afterTool(
      InvocationContext invocationContext,
      BaseTool baseTool,
      Map<String, Object> input,
      ToolContext toolContext,
      Object response) {
    GuardrailAuditService.record(
        invocationContext.session().id(), "OUTPUT", "TOOL:" + baseTool.name(), "ALLOWED");
    return Optional.empty();
  }
}
