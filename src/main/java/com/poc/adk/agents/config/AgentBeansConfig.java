package com.poc.adk.agents.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.models.BaseLlm;
import com.poc.adk.agents.hitl.HitlApprovalAgent;
import com.poc.adk.agents.loop.LoopRefinementAgent;
import com.poc.adk.agents.parallel.ParallelInvestigationAgent;
import com.poc.adk.agents.personalization.MemoryPersonalizationAgent;
import com.poc.adk.agents.rag.RagPolicyAgent;
import com.poc.adk.agents.routing.DynamicRoutingAgent;
import com.poc.adk.agents.sequential.SequentialInvestigationAgent;
import com.poc.adk.agents.singleagent.SingleAgent;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.tools.FraudSignalTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.RefundTool;
import com.poc.adk.tools.ShipmentTrackingTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentBeansConfig {

  @Bean
  public BaseAgent singleAgent(
      BaseLlm baseLlm, OrderLookupTool orderLookupTool, GuardrailAuditService auditService) {
    return SingleAgent.create(baseLlm, orderLookupTool, auditService);
  }

  @Bean
  public BaseAgent sequentialInvestigationAgent(
      BaseLlm baseLlm,
      OrderLookupTool orderLookupTool,
      PaymentHistoryTool paymentHistoryTool,
      ShipmentTrackingTool shipmentTrackingTool,
      PolicyRetrievalTool policyRetrievalTool,
      GuardrailAuditService auditService) {
    return SequentialInvestigationAgent.create(
        baseLlm,
        baseLlm,
        baseLlm,
        orderLookupTool,
        paymentHistoryTool,
        shipmentTrackingTool,
        policyRetrievalTool,
        auditService);
  }

  @Bean
  public BaseAgent parallelInvestigationAgent(
      BaseLlm baseLlm,
      PaymentHistoryTool paymentHistoryTool,
      ShipmentTrackingTool shipmentTrackingTool,
      FraudSignalTool fraudSignalTool,
      GuardrailAuditService auditService) {
    return ParallelInvestigationAgent.create(
        baseLlm,
        baseLlm,
        baseLlm,
        baseLlm,
        paymentHistoryTool,
        shipmentTrackingTool,
        fraudSignalTool,
        auditService);
  }

  @Bean
  public BaseAgent dynamicRoutingAgent(
      BaseLlm baseLlm,
      OrderLookupTool orderLookupTool,
      PolicyRetrievalTool policyRetrievalTool,
      ShipmentTrackingTool shipmentTrackingTool,
      CustomerPreferenceTool customerPreferenceTool,
      GuardrailAuditService auditService) {
    return DynamicRoutingAgent.create(
        baseLlm,
        baseLlm,
        baseLlm,
        baseLlm,
        orderLookupTool,
        policyRetrievalTool,
        shipmentTrackingTool,
        customerPreferenceTool,
        auditService);
  }

  @Bean
  public BaseAgent hitlApprovalAgent(
      BaseLlm baseLlm, RefundTool refundTool, GuardrailAuditService auditService) {
    return HitlApprovalAgent.create(baseLlm, refundTool, auditService);
  }

  @Bean
  public BaseAgent loopRefinementAgent(
      BaseLlm baseLlm, GuardrailAuditService auditService) {
    return LoopRefinementAgent.create(baseLlm, baseLlm, baseLlm, auditService);
  }

  @Bean
  public BaseAgent ragPolicyAgent(
      BaseLlm baseLlm,
      PolicyRetrievalTool policyRetrievalTool,
      GuardrailAuditService auditService) {
    return RagPolicyAgent.create(baseLlm, policyRetrievalTool, auditService);
  }

  @Bean
  public BaseAgent memoryPersonalizationAgent(
      BaseLlm baseLlm,
      CustomerPreferenceTool customerPreferenceTool,
      GuardrailAuditService auditService) {
    return MemoryPersonalizationAgent.create(baseLlm, customerPreferenceTool, auditService);
  }
}
