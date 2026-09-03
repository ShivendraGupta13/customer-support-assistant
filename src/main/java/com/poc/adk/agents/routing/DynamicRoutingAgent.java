package com.poc.adk.agents.routing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.ShipmentTrackingTool;

/** Playbook §4 — coordinator routes to billing / shipping / account specialists. */
public final class DynamicRoutingAgent {

  public static final BaseAgent ROOT_AGENT =
      create(
          new ContextLlm(),
          new ContextLlm(),
          new ContextLlm(),
          new ContextLlm(),
          new OrderLookupTool(null),
          new PolicyRetrievalTool(null),
          new ShipmentTrackingTool(null),
          new CustomerPreferenceTool(null),
          null);

  public static LlmAgent create(
      BaseLlm coordinatorModel,
      BaseLlm billingModel,
      BaseLlm shippingModel,
      BaseLlm accountModel,
      OrderLookupTool orderLookupTool,
      PolicyRetrievalTool policyRetrievalTool,
      ShipmentTrackingTool shipmentTrackingTool,
      CustomerPreferenceTool customerPreferenceTool,
      GuardrailAuditService auditService) {
    LlmAgent billing =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("billing")
                    .description("Billing specialist for refunds and payments")
                    .model(billingModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-billing.v1.md"))
                    .tools(
                        FunctionTool.create(orderLookupTool, "orderLookup"),
                        FunctionTool.create(policyRetrievalTool, "policyRetrieve"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    LlmAgent shipping =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("shipping")
                    .description("Shipping specialist for delays and tracking")
                    .model(shippingModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-shipping.v1.md"))
                    .tools(
                        FunctionTool.create(orderLookupTool, "orderLookup"),
                        FunctionTool.create(shipmentTrackingTool, "shipmentTracking"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    LlmAgent account =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("account")
                    .description("Account specialist for contact preferences")
                    .model(accountModel)
                    .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-account.v1.md"))
                    .tools(FunctionTool.create(customerPreferenceTool, "customerPreference"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    return Guardrails.apply(
            LlmAgent.builder()
                .name("demo-dynamic-routing")
                .description("Coordinator routes queries to billing, shipping, or account specialists")
                .model(coordinatorModel)
                .instruction(AgentPrompts.load("prompts/demo-dynamic-routing-coordinator.v1.md"))
                .subAgents(billing, shipping, account)
                .generateContentConfig(AgentModels.temperatureZero()),
            auditService)
        .build();
  }

  public static LlmAgent create(
      BaseLlm coordinatorModel,
      BaseLlm billingModel,
      BaseLlm shippingModel,
      BaseLlm accountModel,
      OrderLookupTool orderLookupTool,
      PolicyRetrievalTool policyRetrievalTool,
      ShipmentTrackingTool shipmentTrackingTool,
      CustomerPreferenceTool customerPreferenceTool) {
    return create(
        coordinatorModel,
        billingModel,
        shippingModel,
        accountModel,
        orderLookupTool,
        policyRetrievalTool,
        shipmentTrackingTool,
        customerPreferenceTool,
        null);
  }

  private DynamicRoutingAgent() {}
}
