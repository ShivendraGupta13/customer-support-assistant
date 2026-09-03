package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import com.poc.adk.agents.singleagent.SingleAgent;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.util.List;
import java.util.Map;

import com.poc.adk.tools.OrderLookupTool;
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
@Import({ToolIntegrationConfig.class, com.poc.adk.integration.config.GuardrailIntegrationConfig.class})
class SingleAgentEvalTest {

  @Autowired
  private OrderLookupTool orderLookupTool;

  @Autowired
  private GuardrailAuditService auditService;

  @Test
  void layer3_step1_emitsOrderLookupToolCallForOrd5001() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("order_lookup", Map.of("order_id", "ORD-5001")),
            ScriptedLlm.text("Order ORD-5001 status is DELAYED."));

    LlmAgent agent = SingleAgent.create(llm, orderLookupTool, auditService);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(Part.fromText("What's the status of order ORD-5001?")))
            .toList()
            .blockingGet();

    List<FunctionCall> calls =
        events.stream().flatMap(event -> event.functionCalls().stream()).toList();
    assertThat(calls).isNotEmpty();
    assertThat(calls.getFirst().name()).hasValue("order_lookup");
    assertThat(calls.getFirst().args()).hasValueSatisfying(args -> assertThat(args).containsEntry("order_id", "ORD-5001"));

    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).contains("DELAYED");
  }
}
