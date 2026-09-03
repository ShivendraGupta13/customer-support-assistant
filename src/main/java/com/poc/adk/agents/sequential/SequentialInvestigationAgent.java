package com.poc.adk.agents.sequential;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.ShipmentTrackingTool;

/** Playbook §2 — SequentialAgent gather → policy_check → draft via outputKey chaining. */
public final class SequentialInvestigationAgent {

  public static final BaseAgent ROOT_AGENT = create(new ContextLlm(), new ContextLlm(), new ContextLlm());

  public static SequentialAgent create(BaseLlm gatherModel, BaseLlm policyModel, BaseLlm draftModel) {
    LlmAgent gather =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("gather")
                    .description("Collect order, payment, and shipment facts")
                    .model(gatherModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-gather.v1.md"))
                    .tools(
                        FunctionTool.create(OrderLookupTool.class, "orderLookup"),
                        FunctionTool.create(PaymentHistoryTool.class, "paymentHistory"),
                        FunctionTool.create(ShipmentTrackingTool.class, "shipmentTracking"))
                    .outputKey("investigation_facts")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent policyCheck =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("policy_check")
                    .description("Retrieve and summarize applicable policy")
                    .model(policyModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-policy.v1.md"))
                    .tools(FunctionTool.create(PolicyRetrievalTool.class, "policyRetrieve"))
                    .outputKey("policy_findings")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent draft =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("draft")
                    .description("Draft a resolution from facts and policy")
                    .model(draftModel)
                    .instruction(AgentPrompts.load("prompts/demo-sequential-draft.v1.md"))
                    .outputKey("resolution_draft")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    return SequentialAgent.builder()
        .name("demo-sequential-investigation")
        .description("Sequential investigation: gather → policy_check → draft")
        .subAgents(gather, policyCheck, draft)
        .build();
  }

  private SequentialInvestigationAgent() {}
}
