package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import com.poc.adk.agents.rag.RagPolicyAgent;
import com.poc.adk.config.QdrantConfig;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.rag.ChunkingService;
import com.poc.adk.rag.EmbeddingClient;
import com.poc.adk.rag.PolicyChunkIndexer;
import io.qdrant.client.QdrantClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
@Import({GuardrailIntegrationConfig.class, QdrantConfig.class})
class RagPolicyEvalTest {

  private static final String HYBRID_QUERY =
      "Does exception code NW-SHIP-EXC-04 apply to SKU NW-HP-1001?";

  @Autowired QdrantClient qdrantClient;
  @Autowired ChunkingService chunkingService;
  @Autowired EmbeddingClient embeddingClient;
  @Autowired com.poc.adk.tools.PolicyRetrievalTool policyRetrievalTool;
  @Autowired com.poc.adk.guardrails.GuardrailAuditService auditService;

  @BeforeEach
  void indexPolicies() {
    QdrantConfig.recreateCollection(qdrantClient);
    new PolicyChunkIndexer(qdrantClient, chunkingService, embeddingClient).indexFromClasspath();
  }

  @Test
  void layer3_refundQueryCallsPolicyRetrieveAndCitesRefundPolicy() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall(
                "policy_retrieve", Map.of("query", "How many days to request a refund?")),
            ScriptedLlm.text(
                "You have 30 days from delivery to request a refund. "
                    + "Source: refund-policy.md — Refund request window"));

    LlmAgent agent = RagPolicyAgent.create(llm, policyRetrievalTool, auditService);
    List<Event> events = run(agent, "How many days do I have to request a refund?");

    assertPolicyRetrieveCalled(events);
    assertThat(
            events.stream()
                .map(Event::stringifyContent)
                .filter(s -> s != null)
                .reduce("", String::concat))
        .contains("refund-policy.md");
    String finalText = finalText(events);
    assertThat(finalText).containsIgnoringCase("30");
    assertThat(finalText).contains("refund-policy.md");
  }

  @Test
  void layer3_hybridQueryRetrievesLoyaltyChunkForExceptionCodes() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("policy_retrieve", Map.of("query", HYBRID_QUERY)),
            ScriptedLlm.text(
                "Yes, exception code NW-SHIP-EXC-04 applies to SKU NW-HP-1001 for GOLD courtesy refunds. "
                    + "Source: loyalty-policy.md — Courtesy weather-hold exception codes"));

    LlmAgent agent = RagPolicyAgent.create(llm, policyRetrievalTool, auditService);
    List<Event> events = run(agent, HYBRID_QUERY);

    assertPolicyRetrieveCalled(events);
    assertThat(
            events.stream()
                .map(Event::stringifyContent)
                .filter(s -> s != null)
                .reduce("", String::concat))
        .contains("loyalty-policy.md");

    String finalText = finalText(events);
    assertThat(finalText).contains("NW-SHIP-EXC-04").contains("NW-HP-1001");
    assertThat(finalText).contains("loyalty-policy.md");
    assertThat(finalText.toLowerCase()).doesNotContain("shipping-policy.md");
  }

  private static List<Event> run(LlmAgent agent, String userMessage) {
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();
    return runner
        .runAsync(
            "playbook-user",
            session.id(),
            Content.fromParts(Part.fromText(userMessage)))
        .toList()
        .blockingGet();
  }

  private static void assertPolicyRetrieveCalled(List<Event> events) {
    List<FunctionCall> calls =
        events.stream().flatMap(event -> event.functionCalls().stream()).toList();
    assertThat(calls).isNotEmpty();
    assertThat(calls.stream().map(call -> call.name().orElse("")).toList())
        .anyMatch("policy_retrieve"::equals);
  }

  private static String finalText(List<Event> events) {
    return events.stream()
        .filter(Event::finalResponse)
        .map(Event::stringifyContent)
        .filter(s -> s != null)
        .reduce((a, b) -> b)
        .orElse("");
  }
}
