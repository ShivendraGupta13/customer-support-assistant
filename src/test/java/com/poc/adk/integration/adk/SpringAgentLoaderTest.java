package com.poc.adk.integration.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.poc.adk.agents.config.AgentBeansConfig;
import com.poc.adk.eval.ScriptedLlm;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.platform.audit.GuardrailAuditLogRepository;
import com.poc.adk.tools.FraudSignalTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.PolicyRetrievalTool;
import com.poc.adk.tools.RefundTool;
import com.poc.adk.tools.ShipmentTrackingTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SpringAgentLoaderTest {

  private static final List<String> DEMO_AGENT_NAMES =
      List.of(
          "demo-single-agent",
          "demo-sequential-investigation",
          "demo-parallel-investigation",
          "demo-dynamic-routing",
          "demo-hitl-approval",
          "demo-loop-refinement",
          "demo-rag-policy",
          "demo-memory-personalization");

  @Test
  void indexesAgentsByNameAndRejectsUnknown() {
    BaseAgent alpha = LlmAgent.builder().name("alpha").build();
    BaseAgent beta = LlmAgent.builder().name("beta").build();

    SpringAgentLoader loader = new SpringAgentLoader(List.of(alpha, beta));

    assertThat(loader.listAgents()).containsExactly("alpha", "beta");
    assertThat(loader.loadAgent("alpha")).isSameAs(alpha);
    assertThat(loader.loadAgent("beta")).isSameAs(beta);
    assertThatThrownBy(() -> loader.loadAgent("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown agent: missing");
  }

  @Test
  void agentBeansConfigRegistersEightDemoAgentsForLoader() {
    new ApplicationContextRunner()
        .withUserConfiguration(AgentBeansConfig.class, StubAgentDependencies.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Map<String, BaseAgent> agents = context.getBeansOfType(BaseAgent.class);
              assertThat(agents).hasSize(8);
              assertThat(agents.values().stream().map(BaseAgent::name).toList())
                  .containsExactlyInAnyOrderElementsOf(DEMO_AGENT_NAMES);

              SpringAgentLoader loader =
                  new SpringAgentLoader(List.copyOf(agents.values()));
              assertThat(loader.listAgents()).containsExactlyInAnyOrderElementsOf(DEMO_AGENT_NAMES);
              for (String name : DEMO_AGENT_NAMES) {
                assertThat(loader.loadAgent(name).name()).isEqualTo(name);
              }
            });
  }

  @Configuration
  static class StubAgentDependencies {

    @Bean
    BaseLlm baseLlm() {
      return ScriptedLlm.of(ScriptedLlm.text("stub"));
    }

    @Bean
    OrderLookupTool orderLookupTool() {
      return new OrderLookupTool(null);
    }

    @Bean
    PaymentHistoryTool paymentHistoryTool() {
      return new PaymentHistoryTool(null);
    }

    @Bean
    ShipmentTrackingTool shipmentTrackingTool() {
      return new ShipmentTrackingTool(null);
    }

    @Bean
    FraudSignalTool fraudSignalTool() {
      return new FraudSignalTool(null);
    }

    @Bean
    PolicyRetrievalTool policyRetrievalTool() {
      return new PolicyRetrievalTool(null);
    }

    @Bean
    CustomerPreferenceTool customerPreferenceTool() {
      return new CustomerPreferenceTool(null);
    }

    @Bean
    RefundTool refundTool() {
      return new RefundTool(null, null);
    }

    @Bean
    GuardrailAuditService guardrailAuditService() {
      return new GuardrailAuditService(Mockito.mock(GuardrailAuditLogRepository.class));
    }
  }
}
