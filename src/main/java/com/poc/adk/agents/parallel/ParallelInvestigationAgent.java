package com.poc.adk.agents.parallel;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.FraudSignalTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.ShipmentTrackingTool;

/**
 * Playbook §3 — ParallelAgent fan-out (payment / shipment / fraud) then aggregator inside a
 * SequentialAgent root.
 */
public final class ParallelInvestigationAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new ContextLlm(), new ContextLlm(), new ContextLlm());

  public static SequentialAgent create(
      BaseLlm paymentModel, BaseLlm shipmentModel, BaseLlm fraudModel, BaseLlm aggregatorModel) {
    LlmAgent paymentCheck =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("payment_check")
                    .description("Payment status check")
                    .model(paymentModel)
                    .instruction(AgentPrompts.load("prompts/demo-parallel-payment.v1.md"))
                    .tools(FunctionTool.create(PaymentHistoryTool.class, "paymentHistory"))
                    .outputKey("payment_status")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent shipmentCheck =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("shipment_check")
                    .description("Shipment status check")
                    .model(shipmentModel)
                    .instruction(AgentPrompts.load("prompts/demo-parallel-shipment.v1.md"))
                    .tools(FunctionTool.create(ShipmentTrackingTool.class, "shipmentTracking"))
                    .outputKey("shipment_status")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    LlmAgent fraudCheck =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("fraud_check")
                    .description("Fraud signal check")
                    .model(fraudModel)
                    .instruction(AgentPrompts.load("prompts/demo-parallel-fraud.v1.md"))
                    .tools(FunctionTool.create(FraudSignalTool.class, "fraudSignal"))
                    .outputKey("fraud_signal")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    ParallelAgent fanOut =
        ParallelAgent.builder()
            .name("risk_fanout")
            .description("Concurrent payment, shipment, and fraud checks")
            .subAgents(paymentCheck, shipmentCheck, fraudCheck)
            .build();

    LlmAgent aggregator =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("aggregator")
                    .description("Aggregate parallel risk findings")
                    .model(aggregatorModel)
                    .instruction(AgentPrompts.load("prompts/demo-parallel-aggregator.v1.md"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true))
            .build();

    return SequentialAgent.builder()
        .name("demo-parallel-investigation")
        .description("Parallel risk assessment then aggregator")
        .subAgents(fanOut, aggregator)
        .build();
  }

  private ParallelInvestigationAgent() {}
}
