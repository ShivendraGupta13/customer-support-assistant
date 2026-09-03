package com.poc.adk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden-dataset catalog for Layer 2 prompt eval. No LLM, no LLM-as-judge fields.
 */
class EvalCatalogTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<String> FIXTURES =
      List.of(
          "/eval/demo-single-agent.v1.eval.json",
          "/eval/demo-dynamic-routing.v1.eval.json",
          "/eval/demo-hitl-approval.v1.eval.json",
          "/eval/demo-rag-policy.v1.eval.json");

  @Test
  void goldenDatasetsLiveUnderEvalAndHavePromptEvalShape() throws Exception {
    for (String path : FIXTURES) {
      JsonNode root;
      try (InputStream in = EvalCatalogTest.class.getResourceAsStream(path)) {
        assertThat(in).as(path).isNotNull();
        root = MAPPER.readTree(in);
      }
      assertThat(root.get("prompt").asText()).as(path + " prompt").isNotBlank();
      assertThat(root.get("cases").isArray()).as(path + " cases").isTrue();
      assertThat(root.get("cases")).as(path + " cases").isNotEmpty();
      assertThat(root.has("llm_judge") || root.has("judge_prompt"))
          .as(path + " must not use LLM-as-judge")
          .isFalse();
      for (JsonNode testCase : root.get("cases")) {
        assertThat(testCase.get("user_message").asText()).isNotBlank();
        assertThat(testCase.has("frozen_tool_results")).isTrue();
        assertThat(testCase.get("must_contain").isArray()).isTrue();
        assertThat(testCase.get("must_not_contain").isArray()).isTrue();
      }
    }
  }

  @Test
  void specMinimumCoverageCasesArePresent() throws Exception {
    assertFixtureCase("/eval/demo-single-agent.v1.eval.json", "ord-5001-delayed");
    assertFixtureCase("/eval/demo-dynamic-routing.v1.eval.json", "refund-routes-billing");
    assertFixtureCase("/eval/demo-hitl-approval.v1.eval.json", "high-amount-needs-approval");
    assertFixtureCase("/eval/demo-rag-policy.v1.eval.json", "refund-window");
    assertFixtureCase("/eval/demo-rag-policy.v1.eval.json", "loyalty-exception-codes");
  }

  private static void assertFixtureCase(String path, String caseId) throws Exception {
    JsonNode root;
    try (InputStream in = EvalCatalogTest.class.getResourceAsStream(path)) {
      root = MAPPER.readTree(in);
    }
    boolean found = false;
    for (JsonNode testCase : root.get("cases")) {
      if (caseId.equals(testCase.get("id").asText())) {
        found = true;
        break;
      }
    }
    assertThat(found).as("%s case %s", path, caseId).isTrue();
  }
}
