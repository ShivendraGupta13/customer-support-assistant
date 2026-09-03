package com.poc.adk.agents.hitl;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.RefundTool;

/** Playbook §5 — refund above $200 pauses for human confirmation. */
public final class HitlApprovalAgent {

  public static final BaseAgent ROOT_AGENT = create(new ContextLlm());

  public static LlmAgent create(BaseLlm model) {
    return Guardrails.apply(
            LlmAgent.builder()
                .name("demo-hitl-approval")
                .description("Human-in-the-loop refund approval for high amounts")
                .model(model)
                .instruction(AgentPrompts.load("prompts/demo-hitl-approval.v1.md"))
                .tools(FunctionTool.create(RefundTool.class, "processRefund"))
                .generateContentConfig(AgentModels.temperatureZero())
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true))
        .build();
  }

  private HitlApprovalAgent() {}
}
