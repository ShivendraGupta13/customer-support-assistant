package com.poc.adk.agents.routing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.ShipmentTrackingTool;

/** Playbook §4 — coordinator routes to billing / shipping / account specialists. */
public final class DynamicRoutingAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new ContextLlm(), new ContextLlm(), new ContextLlm());

  public static LlmAgent create(
      BaseLlm coordinatorModel,
      BaseLlm billingModel,
      BaseLlm shippingModel,
      BaseLlm accountModel) {
    LlmAgent billing =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("billing")
                    .description("Billing specialist for refunds and payments")
                    .model(billingModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-billing.v1.md"))
                    .tools(
                        FunctionTool.create(OrderLookupTool.class, "orderLookup"),
                        FunctionTool.create(PolicyRetrievalTool.class, "policyRetrieve"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent shipping =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("shipping")
                    .description("Shipping specialist for delays and tracking")
                    .model(shippingModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-shipping.v1.md"))
                    .tools(
                        FunctionTool.create(OrderLookupTool.class, "orderLookup"),
                        FunctionTool.create(ShipmentTrackingTool.class, "shipmentTracking"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent account =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("account")
                    .description("Account specialist for contact preferences")
                    .model(accountModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-account.v1.md"))
                    .tools(FunctionTool.create(CustomerPreferenceTool.class, "customerPreference"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    return Guardrails.apply(
            LlmAgent.builder()
                .name("demo-dynamic-routing")
                .description("Coordinator routes queries to billing, shipping, or account specialists")
                .model(coordinatorModel)
                .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-coordinator.v1.md"))
                .subAgents(billing, shipping, account)
                .generateContentConfig(AgentModels.temperatureZero()))
        .build();
  }

  private DynamicRoutingAgent() {}
}
