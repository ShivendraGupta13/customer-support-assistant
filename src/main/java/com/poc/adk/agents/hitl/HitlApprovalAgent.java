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

import com.poc.adk.guardrails.GuardrailAuditService;

/** Playbook §5 — refund above $200 pauses for human confirmation. */
public final class HitlApprovalAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new RefundTool(null, null), null);

  public static LlmAgent create(
      BaseLlm model, RefundTool refundTool, GuardrailAuditService auditService) {
    var builder =
        LlmAgent.builder()
            .name("demo-hitl-approval")
            .description("Human-in-the-loop refund approval for high amounts")
            .model(model)
            .instruction(AgentPrompts.load("prompts/demo-hitl-approval.v1.md"))
            .tools(FunctionTool.create(refundTool, "processRefund"))
            .generateContentConfig(AgentModels.temperatureZero())
            .disallowTransferToParent(true)
            .disallowTransferToPeers(true);
    return Guardrails.apply(builder, auditService).build();
  }

  public static LlmAgent create(BaseLlm model, RefundTool refundTool) {
    return create(model, refundTool, null);
  }

  private HitlApprovalAgent() {}
}
