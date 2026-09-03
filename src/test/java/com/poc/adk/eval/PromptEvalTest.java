package com.poc.adk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.config.ModelFactory;
import com.poc.adk.config.ModelRoutingProperties;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Layer 2: frozen tool/chunk context + versioned prompt → wording checks. No agent tools in path.
 */
@Tag("llm")
class PromptEvalTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void demoSingleAgent_mentionsDelayedFromFrozenToolJson() throws Exception {
    runPromptEvalCase("/eval/demo-single-agent.v1.eval.json", 0);
  }

  @Test
  void demoDynamicRouting_refundQueryMentionsBillingSpecialist() throws Exception {
    runPromptEvalCase("/eval/demo-dynamic-routing.v1.eval.json", 0);
  }

  @Test
  void demoHitlApproval_highAmountRequestsApprovalWithoutCompletingRefund() throws Exception {
    runPromptEvalCase("/eval/demo-hitl-approval.v1.eval.json", 0);
  }

  @Test
  void demoRagPolicy_refundWindowCitesRefundPolicySection() throws Exception {
    runPromptEvalCase("/eval/demo-rag-policy.v1.eval.json", 0);
  }

  @Test
  void demoRagPolicy_loyaltyExceptionCodesCiteLoyaltyNotShipping() throws Exception {
    runPromptEvalCase("/eval/demo-rag-policy.v1.eval.json", 1);
  }

  private void runPromptEvalCase(String fixturePath, int caseIndex) throws Exception {
    JsonNode root;
    try (InputStream in = PromptEvalTest.class.getResourceAsStream(fixturePath)) {
      root = MAPPER.readTree(in);
    }
    JsonNode testCase = root.get("cases").get(caseIndex);
    String prompt = AgentPrompts.load(root.get("prompt").asText());
    String userMessage = testCase.get("user_message").asText();
    String frozen = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testCase.get("frozen_tool_results"));

    String user =
        userMessage
            + "\n\nTool results (authoritative; answer only from these fields):\n"
            + frozen;

    ModelRoutingProperties properties = new ModelRoutingProperties();
    BaseLlm llm = ModelFactory.create(properties);
    LlmRequest request =
        LlmRequest.builder()
            .model(llm.model())
            .config(GenerateContentConfig.builder().temperature(0f).build())
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(prompt + "\n\n" + user)))
                        .build()))
            .build();

    List<LlmResponse> responses = new ArrayList<>();
    llm.generateContent(request, false).blockingForEach(responses::add);
    String reply =
        responses.stream()
            .map(
                r ->
                    r.content()
                        .flatMap(Content::parts)
                        .filter(parts -> !parts.isEmpty())
                        .flatMap(parts -> parts.getFirst().text())
                        .orElse(""))
            .reduce("", String::concat);

    for (JsonNode required : testCase.get("must_contain")) {
      assertThat(reply).containsIgnoringCase(required.asText());
    }
    for (JsonNode forbidden : testCase.get("must_not_contain")) {
      assertThat(reply).doesNotContain(forbidden.asText());
    }
  }
}
