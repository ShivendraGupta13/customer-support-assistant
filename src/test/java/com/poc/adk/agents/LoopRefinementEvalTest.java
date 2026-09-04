package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.poc.adk.agents.loop.LoopRefinementAgent;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.util.List;
import java.util.Map;
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
class LoopRefinementEvalTest {

  @org.springframework.beans.factory.annotation.Autowired
  private com.poc.adk.guardrails.GuardrailAuditService auditService;

  @Test
  void layer3_runsAtLeastTwoDraftCritiqueIterationsThenPublishesFinalDraft() {
    ScriptedLlm drafterLlm =
        ScriptedLlm.of(
            ScriptedLlm.text("Sorry about the delay."),
            ScriptedLlm.text(
                "We sincerely apologize for the delay on order ORD-5001. We are working to deliver soon."));
    ScriptedLlm criticLlm =
        ScriptedLlm.of(
            ScriptedLlm.text("Add empathy and mention ORD-5001 explicitly."),
            ScriptedLlm.functionCall("exit_loop", Map.of()));
    ScriptedLlm publisherLlm =
        ScriptedLlm.of(
            ScriptedLlm.text(
                "We sincerely apologize for the delay on order ORD-5001. We are working to deliver soon."));

    SequentialAgent agent =
        LoopRefinementAgent.create(drafterLlm, criticLlm, publisherLlm, auditService);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(
                    Part.fromText(
                        "Draft a customer-facing apology message for the ORD-5001 delay, and make sure it follows our tone policy.")))
            .toList()
            .blockingGet();

    long drafterTurns = events.stream().filter(event -> "drafter".equals(event.author())).count();
    long criticTurns = events.stream().filter(event -> "critic".equals(event.author())).count();
    assertThat(drafterTurns).isGreaterThanOrEqualTo(2);
    assertThat(criticTurns).isGreaterThanOrEqualTo(2);

    List<String> authors = events.stream().map(Event::author).toList();
    assertThat(authors).contains("publisher");
    int lastCritic = authors.lastIndexOf("critic");
    int firstPublisher = authors.indexOf("publisher");
    assertThat(firstPublisher).isGreaterThan(lastCritic);

    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).contains("ORD-5001");
    assertThat(finalText).doesNotContain("Add empathy");
  }

  @Test
  void layer3_forcedFailHitsMaxIterationsAndStillPublishes() {
    ScriptedLlm drafterLlm =
        ScriptedLlm.of(
            ScriptedLlm.text("Delay."),
            ScriptedLlm.text("Delay."),
            ScriptedLlm.text("Delay."));
    ScriptedLlm criticLlm =
        ScriptedLlm.of(
            ScriptedLlm.text("Never compliant — always revise."),
            ScriptedLlm.text("Never compliant — always revise."),
            ScriptedLlm.text("Never compliant — always revise."));
    ScriptedLlm publisherLlm =
        ScriptedLlm.of(ScriptedLlm.text("Best-effort apology for ORD-5001 delay despite open critiques."));

    SequentialAgent agent =
        LoopRefinementAgent.create(drafterLlm, criticLlm, publisherLlm, auditService);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(
                    Part.fromText(
                        "Draft an apology for ORD-5001. Use the unsatisfiable instruction: never say apologize.")))
            .toList()
            .blockingGet();

    long drafterTurns = events.stream().filter(event -> "drafter".equals(event.author())).count();
    assertThat(drafterTurns).isLessThanOrEqualTo(3);

    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).isNotBlank();
    assertThat(events.stream().map(Event::author)).contains("publisher");
  }

  @Test
  void layer3_earlyExitLoopOnFirstCritiqueIsIgnoredUntilASecondPass() {
    ScriptedLlm drafterLlm =
        ScriptedLlm.of(
            ScriptedLlm.text("Sorry about the delay."),
            ScriptedLlm.text(
                "We sincerely apologize for the delay on order ORD-5001. We are working to deliver soon."),
            ScriptedLlm.text(
                "We sincerely apologize for the delay on order ORD-5001. We are working to deliver soon."));
    ScriptedLlm criticLlm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("exit_loop", Map.of()),
            ScriptedLlm.text("Add empathy and mention ORD-5001 explicitly."),
            ScriptedLlm.functionCall("exit_loop", Map.of()));
    ScriptedLlm publisherLlm =
        ScriptedLlm.of(
            ScriptedLlm.text(
                "We sincerely apologize for the delay on order ORD-5001. We are working to deliver soon."));

    SequentialAgent agent =
        LoopRefinementAgent.create(drafterLlm, criticLlm, publisherLlm, auditService);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> events =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(
                    Part.fromText(
                        "Draft a customer-facing apology message for the ORD-5001 delay, and make sure it follows our tone policy.")))
            .toList()
            .blockingGet();

    long drafterTurns = events.stream().filter(event -> "drafter".equals(event.author())).count();
    long criticTurns = events.stream().filter(event -> "critic".equals(event.author())).count();
    assertThat(drafterTurns).isGreaterThanOrEqualTo(2);
    assertThat(criticTurns).isGreaterThanOrEqualTo(2);

    List<String> authors = events.stream().map(Event::author).toList();
    assertThat(authors).contains("publisher");
    int lastCritic = authors.lastIndexOf("critic");
    int firstPublisher = authors.indexOf("publisher");
    assertThat(firstPublisher).isGreaterThan(lastCritic);

    String finalText =
        events.stream()
            .filter(Event::finalResponse)
            .map(Event::stringifyContent)
            .reduce((a, b) -> b)
            .orElse("");
    assertThat(finalText).contains("ORD-5001");
    assertThat(finalText).doesNotContain("Add empathy");
  }
}
