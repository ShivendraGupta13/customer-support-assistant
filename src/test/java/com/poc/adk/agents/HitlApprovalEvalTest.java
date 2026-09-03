package com.poc.adk.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.poc.adk.agents.hitl.HitlApprovalAgent;
import com.poc.adk.commerce.payment.PaymentRepository;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.integration.config.GuardrailIntegrationConfig;
import com.poc.adk.integration.config.ToolIntegrationConfig;
import com.poc.adk.tools.RefundTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=optional:classpath:do-not-run-schema.sql",
      "spring.sql.init.data-locations=classpath:data.sql"
    })
@Import({ToolIntegrationConfig.class, GuardrailIntegrationConfig.class})
@TestPropertySource(properties = "poc.eval.hitl-isolated=true")
class HitlApprovalEvalTest {

  private static final String ORDER_ID = "ORD-5010";

  @Autowired PaymentRepository payments;
  @Autowired RefundTool refundTool;

  @BeforeEach
  void resetOrd5010PaymentToCaptured() {
    var payment = payments.findByOrderId(ORDER_ID).orElseThrow();
    payment.setStatus("CAPTURED");
    payments.saveAndFlush(payment);
  }

  @Test
  void layer3_highAmountRefundRequestsConfirmationThenApproves() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("process_refund", Map.of("order_id", ORDER_ID)),
            ScriptedLlm.text("This refund requires manager approval before processing."),
            ScriptedLlm.functionCall("process_refund", Map.of("order_id", ORDER_ID)),
            ScriptedLlm.text("Refund for ORD-5010 has been processed."));

    LlmAgent agent = HitlApprovalAgent.create(llm);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> firstTurn =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(Part.fromText("Process a refund for ORD-5010 (USD 350).")))
            .toList()
            .blockingGet();

    assertThat(paymentStatus()).isEqualTo("CAPTURED");
    String confirmationId = findConfirmationCallId(firstTurn);
    assertThat(confirmationId).isNotBlank();

    List<Event> secondTurn =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(confirmationResponse(confirmationId, true)))
            .toList()
            .blockingGet();

    assertThat(secondTurn).isNotEmpty();
    assertThat(paymentStatus()).isEqualTo("REFUNDED");
  }

  @Test
  void layer3_rejectLeavesPaymentUnchanged() {
    ScriptedLlm llm =
        ScriptedLlm.of(
            ScriptedLlm.functionCall("process_refund", Map.of("order_id", ORDER_ID)),
            ScriptedLlm.text("This refund requires manager approval before processing."),
            ScriptedLlm.functionCall("process_refund", Map.of("order_id", ORDER_ID)),
            ScriptedLlm.text("The refund was not approved."));

    LlmAgent agent = HitlApprovalAgent.create(llm);
    InMemoryRunner runner = new InMemoryRunner(agent);
    Session session =
        runner.sessionService().createSession(agent.name(), "playbook-user").blockingGet();

    List<Event> firstTurn =
        runner
            .runAsync(
                "playbook-user",
                session.id(),
                Content.fromParts(Part.fromText("Process a refund for ORD-5010 (USD 350).")))
            .toList()
            .blockingGet();

    String confirmationId = findConfirmationCallId(firstTurn);

    runner
        .runAsync(
            "playbook-user",
            session.id(),
            Content.fromParts(confirmationResponse(confirmationId, false)))
        .toList()
        .blockingGet();

    assertThat(paymentStatus()).isEqualTo("CAPTURED");
  }

  private String paymentStatus() {
    return payments.findByOrderId(ORDER_ID).orElseThrow().getStatus();
  }

  private static String findConfirmationCallId(List<Event> events) {
    Optional<String> id =
        events.stream()
            .flatMap(event -> event.functionCalls().stream())
            .filter(call -> "adk_request_confirmation".equals(call.name().orElse("")))
            .map(call -> call.id().orElse(""))
            .filter(found -> !found.isBlank())
            .findFirst();
    assertThat(id).isPresent();
    return id.get();
  }

  private static Part confirmationResponse(String confirmationCallId, boolean confirmed) {
    return Part.builder()
        .functionResponse(
            FunctionResponse.builder()
                .id(confirmationCallId)
                .name("adk_request_confirmation")
                .response(Map.of("confirmed", confirmed, "payload", Map.of()))
                .build())
        .build();
  }
}
