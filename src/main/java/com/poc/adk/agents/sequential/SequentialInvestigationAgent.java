package com.poc.adk.agents.sequential;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LlmAgent.IncludeContents;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.ShipmentTrackingTool;
import io.reactivex.rxjava3.core.Maybe;

/** Playbook §2 — SequentialAgent gather → policy_check → draft via outputKey chaining. */
public final class SequentialInvestigationAgent {

  public static SequentialAgent create(
      BaseLlm gatherModel,
      BaseLlm policyModel,
      BaseLlm draftModel,
      OrderLookupTool orderLookupTool,
      PaymentHistoryTool paymentHistoryTool,
      ShipmentTrackingTool shipmentTrackingTool,
      PolicyRetrievalTool policyRetrievalTool,
      GuardrailAuditService auditService) {
    LlmAgent gather =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("gather")
                    .description("Collect order, payment, and shipment facts")
                    .model(gatherModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-gather.v1.md"))
                    .includeContents(IncludeContents.NONE)
                    .tools(
                        FunctionTool.create(orderLookupTool, "orderLookup"),
                        FunctionTool.create(paymentHistoryTool, "paymentHistory"),
                        FunctionTool.create(shipmentTrackingTool, "shipmentTracking"))
                    .outputKey("investigation_facts")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    LlmAgent policyCheck =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("policy_check")
                    .description("Retrieve and summarize applicable policy")
                    .model(policyModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-policy.v1.md"))
                    .includeContents(IncludeContents.NONE)
                    .tools(FunctionTool.create(policyRetrievalTool, "policyRetrieve"))
                    .outputKey("policy_findings")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    LlmAgent draft =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("draft")
                    .description("Draft a resolution from facts and policy")
                    .model(draftModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-draft.v1.md"))
                    .includeContents(IncludeContents.NONE)
                    .outputKey("resolution_draft")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    return SequentialAgent.builder()
        .name("demo-sequential-investigation")
        .description("Sequential investigation: gather → policy_check → draft")
        .subAgents(gather, policyCheck, draft)
        .beforeAgentCallback(SequentialInvestigationAgent::clearPriorInvestigationState)
        .build();
  }

  public static SequentialAgent create(
      BaseLlm gatherModel,
      BaseLlm policyModel,
      BaseLlm draftModel,
      OrderLookupTool orderLookupTool,
      PaymentHistoryTool paymentHistoryTool,
      ShipmentTrackingTool shipmentTrackingTool,
      PolicyRetrievalTool policyRetrievalTool) {
    return create(
        gatherModel,
        policyModel,
        draftModel,
        orderLookupTool,
        paymentHistoryTool,
        shipmentTrackingTool,
        policyRetrievalTool,
        null);
  }

  private static Maybe<com.google.genai.types.Content> clearPriorInvestigationState(
      CallbackContext ctx) {
    ctx.state().remove("investigation_facts");
    ctx.state().remove("policy_findings");
    ctx.state().remove("resolution_draft");
    return Maybe.empty();
  }

  private SequentialInvestigationAgent() {}
}
