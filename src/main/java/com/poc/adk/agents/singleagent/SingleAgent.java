package com.poc.adk.agents.singleagent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.OrderLookupTool;

/** Playbook §1 — single LlmAgent + order_lookup + short-term memory. */
public final class SingleAgent {

  public static final BaseAgent ROOT_AGENT = create(new ContextLlm());

  public static LlmAgent create(BaseLlm model) {
    return Guardrails.apply(
            LlmAgent.builder()
                .name("demo-single-agent")
                .description("Single-agent order status lookup with short-term memory")
                .model(model)
                .instruction(AgentPrompts.load("prompts/demo-single-agent.v1.md"))
                .tools(FunctionTool.create(OrderLookupTool.class, "orderLookup"))
                .generateContentConfig(AgentModels.temperatureZero())
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true))
        .build();
  }

  private SingleAgent() {}
}
