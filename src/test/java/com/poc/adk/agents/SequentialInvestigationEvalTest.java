package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.poc.adk.agents.sequential.SequentialInvestigationAgent;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.util.List;
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
class SequentialInvestigationEvalTest {

  @Test
  void layer3_runsGatherThenPolicyThenDraft_andChainsOutputKeys() {
    ScriptedLlm gatherLlm =
        ScriptedLlm.of(
            ScriptedLlm.text(
                "Order ORD-5001 DELAYED; payment CAPTURED 129.99; shipment SHP-7001 IN_TRANSIT_DELAYED 6 days."));
    ScriptedLlm policyLlm =
        ScriptedLlm.of(ScriptedLlm.text("Shipping delay policy applies; courtesy hold may apply for GOLD."));
    ScriptedLlm draftLlm =
        ScriptedLlm.of(
            ScriptedLlm.text(
                "ORD-5001 is delayed in transit. Per shipping policy we can offer a courtesy update."));

    SequentialAgent agent =
        SequentialInvestigationAgent.create(gatherLlm, policyLlm, draftLlm);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(
                    Part.fromText("Investigate order ORD-5001 and tell me what's going on.")))
            .toList()
            .blockingGet();

    List<String> stageAuthors =
        events.stream()
            .map(Event::author)
            .filter(author -> List.of("gather", "policy_check", "draft").contains(author))
            .distinct()
            .toList();
    assertThat(stageAuthors).containsExactly("gather", "policy_check", "draft");

    Session after =
        runner
            .sessionService()
            .getSession(agent.name(), "playbook-user", session.id(), java.util.Optional.empty())
            .blockingGet();
    assertThat(after.state()).containsKeys("investigation_facts", "policy_findings");
    assertThat(String.valueOf(after.state().get("investigation_facts"))).contains("ORD-5001");
    assertThat(String.valueOf(after.state().get("policy_findings"))).containsIgnoringCase("shipping");

    // Policy stage instruction received gather outputKey via {investigation_facts}.
    assertThat(policyLlm.requests()).isNotEmpty();
    String policyInstructions =
        String.join("\n", policyLlm.requests().getFirst().getSystemInstructions());
    assertThat(policyInstructions).contains("ORD-5001");
  }

  @Test
  void layer3_missingOrderCompletesPipelineWithoutCrash() {
    ScriptedLlm gatherLlm = ScriptedLlm.of(ScriptedLlm.text("Order ORD-9999 was not found."));
    ScriptedLlm policyLlm =
        ScriptedLlm.of(ScriptedLlm.text("No policy check applies because the order was not found."));
    ScriptedLlm draftLlm =
        ScriptedLlm.of(ScriptedLlm.text("I could not find order ORD-9999 in our records."));

    SequentialAgent agent =
        SequentialInvestigationAgent.create(gatherLlm, policyLlm, draftLlm);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(Part.fromText("Investigate order ORD-9999.")))
            .toList()
            .blockingGet();

    assertThat(events).isNotEmpty();
    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).containsIgnoringCase("ORD-9999");
    assertThat(finalText).containsIgnoringCase("could not find");
  }
}
