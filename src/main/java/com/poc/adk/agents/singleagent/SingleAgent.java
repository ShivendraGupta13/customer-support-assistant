package com.poc.adk.agents.singleagent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.OrderLookupTool;

/** Playbook §1 — single LlmAgent + order_lookup + short-term memory. */
public final class SingleAgent {

  public static LlmAgent create(
      BaseLlm model, OrderLookupTool orderLookupTool, GuardrailAuditService auditService) {
    var builder =
        LlmAgent.builder()
            .name("demo-single-agent")
            .description("Single-agent order status lookup with short-term memory")
            .model(model)
            .instruction(AgentPrompts.load("prompts/demo-single-agent.v1.md"))
            .tools(FunctionTool.create(orderLookupTool, "orderLookup"))
            .generateContentConfig(AgentModels.temperatureZero())
            .disallowTransferToParent(true)
            .disallowTransferToPeers(true);
    return Guardrails.apply(builder, auditService).build();
  }

  public static LlmAgent create(BaseLlm model, OrderLookupTool orderLookupTool) {
    return create(model, orderLookupTool, null);
  }

  private SingleAgent() {}
}
