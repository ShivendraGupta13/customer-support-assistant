package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.poc.adk.agents.routing.DynamicRoutingAgent;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.util.List;
import java.util.Map;

import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.ShipmentTrackingTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=optional:classpath:do-not-run-schema.sql",
      "spring.sql.init.data-locations=classpath:data.sql"
    })
@Import({ToolIntegrationConfig.class, GuardrailIntegrationConfig.class})
class DynamicRoutingEvalTest {

  @Autowired
  private OrderLookupTool orderLookupTool;

  @Autowired
  private ShipmentTrackingTool shipmentTrackingTool;

  @Autowired
  private CustomerPreferenceTool customerPreferenceTool;

  @Autowired
  private GuardrailAuditService auditService;

  @Autowired(required = false)
  private PolicyRetrievalTool policyRetrievalTool;

  @Test
  void layer3_refundQueryTransfersToBilling() {
    assertTransferTarget(
        "I want a refund for ORD-5001.",
        "billing",
        ScriptedLlm.of(
            ScriptedLlm.functionCall("transfer_to_agent", Map.of("agent_name", "billing"))),
        ScriptedLlm.of(ScriptedLlm.text("Refund eligibility for ORD-5001 reviewed per policy.")));
  }

  @Test
  void layer3_delayQueryTransfersToShipping() {
    assertTransferTarget(
        "Why is my package late for ORD-5001?",
        "shipping",
        ScriptedLlm.of(
            ScriptedLlm.functionCall("transfer_to_agent", Map.of("agent_name", "shipping"))),
        ScriptedLlm.of(
            ScriptedLlm.text("ORD-5001 shipment SHP-7001 is IN_TRANSIT_DELAYED 6 days.")));
  }

  @Test
  void layer3_preferenceQueryTransfersToAccount() {
    assertTransferTarget(
        "Update my contact preference to SMS.",
        "account",
        ScriptedLlm.of(
            ScriptedLlm.functionCall("transfer_to_agent", Map.of("agent_name", "account"))),
        ScriptedLlm.of(ScriptedLlm.text("I will update your contact preference to SMS.")));
  }

  private void assertTransferTarget(
      String userMessage,
      String expectedSpecialist,
      ScriptedLlm coordinatorLlm,
      ScriptedLlm specialistLlm) {
    PolicyRetrievalTool retrievalTool =
        policyRetrievalTool != null ? policyRetrievalTool : new PolicyRetrievalTool(null);
    LlmAgent agent =
        DynamicRoutingAgent.create(
            coordinatorLlm,
            specialistLlm,
            specialistLlm,
            specialistLlm,
            orderLookupTool,
            retrievalTool,
            shipmentTrackingTool,
            customerPreferenceTool,
            auditService);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(Part.fromText(userMessage)))
            .toList()
            .blockingGet();

    List<String> transferTargets =
        events.stream()
            .map(Event::actions)
            .filter(actions -> actions.transferToAgent().isPresent())
            .map(actions -> actions.transferToAgent().get())
            .toList();
    assertThat(transferTargets).contains(expectedSpecialist);
    assertThat(events.stream().map(Event::author)).contains(expectedSpecialist);
  }
}
