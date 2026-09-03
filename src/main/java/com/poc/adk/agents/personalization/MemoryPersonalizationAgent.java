package com.poc.adk.agents.personalization;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.memory.CustomerPreferenceTool;

import com.poc.adk.guardrails.GuardrailAuditService;

/** Playbook §8 — long-term contact preference from session-bound {@code customer_id}. */
public final class MemoryPersonalizationAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new CustomerPreferenceTool(null), null);

  public static LlmAgent create(
      BaseLlm model, CustomerPreferenceTool preferenceTool, GuardrailAuditService auditService) {
    var builder =
        LlmAgent.builder()
            .name("demo-memory-personalization")
            .description("Recalls long-term contact preference from session-bound customer_id")
            .model(model)
            .instruction(AgentPrompts.load("prompts/demo-memory-personalization.v1.md"))
            .tools(FunctionTool.create(preferenceTool, "customerPreference"))
            .generateContentConfig(AgentModels.temperatureZero())
            .disallowTransferToParent(true)
            .disallowTransferToPeers(true);
    return Guardrails.apply(builder, auditService).build();
  }

  public static LlmAgent create(BaseLlm model, CustomerPreferenceTool preferenceTool) {
    return create(model, preferenceTool, null);
  }

  private MemoryPersonalizationAgent() {}
}
