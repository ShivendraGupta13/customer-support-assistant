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

/** Playbook §8 — long-term contact preference from session-bound {@code customer_id}. */
public final class MemoryPersonalizationAgent {

  public static final BaseAgent ROOT_AGENT = create(new ContextLlm());

  public static LlmAgent create(BaseLlm model) {
    return Guardrails.apply(
            LlmAgent.builder()
                .name("demo-memory-personalization")
                .description("Recalls long-term contact preference from session-bound customer_id")
                .model(model)
                .instruction(AgentPrompts.load("prompts/demo-memory-personalization.v1.md"))
                .tools(FunctionTool.create(CustomerPreferenceTool.class, "customerPreference"))
                .generateContentConfig(AgentModels.temperatureZero())
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true))
        .build();
  }

  private MemoryPersonalizationAgent() {}
}
