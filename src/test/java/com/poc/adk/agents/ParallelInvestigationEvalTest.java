package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.poc.adk.agents.parallel.ParallelInvestigationAgent;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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
class ParallelInvestigationEvalTest {

  @Test
  void layer3_threeCheckToolCallsThenAggregator_withoutStrictOrder() {
    ScriptedLlm paymentLlm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("payment_history", Map.of("order_id", "ORD-5002")),
            ScriptedLlm.text("Payment CAPTURED amount 249.00"));
    ScriptedLlm shipmentLlm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("shipment_tracking", Map.of("order_id", "ORD-5002")),
            ScriptedLlm.text("Shipment SHP-7002 status PLACED days_delayed 0"));
    ScriptedLlm fraudLlm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("fraud_signal", Map.of("order_id", "ORD-5002")),
            ScriptedLlm.text("Fraud MULTIPLE_SHIPPING_ADDRESSES score 0.82"));
    ScriptedLlm aggregatorLlm =
        ScriptedLlm.of(
            ScriptedLlm.text(
                "Risk report: payment CAPTURED; shipment present; fraud MULTIPLE_SHIPPING_ADDRESSES score 0.82."));

    SequentialAgent agent =
        ParallelInvestigationAgent.create(paymentLlm, shipmentLlm, fraudLlm, aggregatorLlm);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(
                    Part.fromText("Give me a full risk assessment on order ORD-5002.")))
            .toList()
            .blockingGet();

    Set<String> toolNames =
        events.stream()
            .flatMap(event -> event.functionCalls().stream())
            .map(fc -> fc.name().orElse(""))
            .collect(Collectors.toSet());
    assertThat(toolNames)
        .containsExactlyInAnyOrder("payment_history", "shipment_tracking", "fraud_signal");

    // Aggregator author appears after all three check authors have appeared at least once.
    List<String> authors = events.stream().map(Event::author).toList();
    int lastCheck =
        Math.max(
            Math.max(authors.lastIndexOf("payment_check"), authors.lastIndexOf("shipment_check")),
            authors.lastIndexOf("fraud_check"));
    int firstAggregator = authors.indexOf("aggregator");
    assertThat(firstAggregator).isGreaterThan(lastCheck);

    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).contains("MULTIPLE_SHIPPING_ADDRESSES");
    assertThat(finalText).contains("0.82");
  }
}
