package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import com.poc.adk.agents.personalization.MemoryPersonalizationAgent;
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
class MemoryPersonalizationEvalTest {

  private static final String USER_MESSAGE = "What's the best way to reach me?";

  @Test
  void layer3_cust1001SessionRecallsEmailPreference() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("customer_preference", Map.of()),
            ScriptedLlm.text("The best way to reach you is email."));

    List<Event> events = run(MemoryPersonalizationAgent.create(llm), "CUST-1001");

    assertCustomerPreferenceCalled(events);
    assertThat(finalText(events)).containsIgnoringCase("email");
  }

  @Test
  void layer3_cust1002SessionRecallsSmsPreference() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("customer_preference", Map.of()),
            ScriptedLlm.text("The best way to reach you is SMS."));

    List<Event> events = run(MemoryPersonalizationAgent.create(llm), "CUST-1002");

    assertCustomerPreferenceCalled(events);
    assertThat(finalText(events)).containsIgnoringCase("SMS");
  }

  private static List<Event> run(LlmAgent agent, String customerId) {
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner
            .sessionService()
            .createSession(
                agent.name(),
                "playbook-user",
                Map.of("customer_id", customerId),
                "session-" + customerId)
            .blockingGet();
    return runner
        .runAsync("playbook-user", session.id(), Content.fromParts(Part.fromText(USER_MESSAGE)))
        .toList()
        .blockingGet();
  }

  private static void assertCustomerPreferenceCalled(List<Event> events) {
    List<FunctionCall> calls =
        events.stream().flatMap(event -> event.functionCalls().stream()).toList();
    assertThat(calls).isNotEmpty();
    assertThat(calls.stream().map(call -> call.name().orElse("")).toList())
        .anyMatch("customer_preference"::equals);
  }

  private static String finalText(List<Event> events) {
    return events.stream()
        .filter(Event::finalResponse)
        .map(Event::stringifyContent)
        .reduce((a, b) -> b)
        .orElse("");
  }
}
