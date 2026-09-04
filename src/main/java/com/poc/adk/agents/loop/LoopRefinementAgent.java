package com.poc.adk.agents.loop;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ExitLoopTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.guardrails.Guardrails;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Map;
import java.util.Optional;

/** Playbook §6 — LoopAgent draft→critique until exit, then publisher emits final draft only. */
public final class LoopRefinementAgent {

  private static final String CRITIQUE_PASS_KEY = "critique_pass";

  public static SequentialAgent create(
      BaseLlm drafterModel,
      BaseLlm criticModel,
      BaseLlm publisherModel,
      GuardrailAuditService auditService) {
    LlmAgent drafter =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("drafter")
                    .description("Draft customer-facing apology")
                    .model(drafterModel)
                    .instruction(AgentPrompts.load("prompts/demo-loop-drafter.v1.md"))
                    .outputKey("draft")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    LlmAgent critic =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("critic")
                    .description("Critique draft against tone policy")
                    .model(criticModel)
                    .instruction(AgentPrompts.load("prompts/demo-loop-critic.v1.md"))
                    .tools(ExitLoopTool.INSTANCE)
                    .outputKey("critique")
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .beforeAgentCallbackSync(LoopRefinementAgent::incrementCritiquePass)
            .beforeToolCallbackSync(LoopRefinementAgent::blockPrematureExitLoop)
            .build();

    LoopAgent refinementLoop =
        LoopAgent.builder()
            .name("refinement_loop")
            .description("Draft and critique until policy-compliant or max iterations")
            .subAgents(drafter, critic)
            .maxIterations(3)
            .build();

    LlmAgent publisher =
        Guardrails.apply(
                LlmAgent.builder()
                    .name("publisher")
                    .description("Publish final refined draft only")
                    .model(publisherModel)
                    .instruction(AgentPrompts.load("prompts/demo-loop-publisher.v1.md"))
                    .generateContentConfig(AgentModels.temperatureZero())
                    .disallowTransferToParent(true)
                    .disallowTransferToPeers(true),
                auditService)
            .build();

    return SequentialAgent.builder()
        .name("demo-loop-refinement")
        .description("Loop refinement then publish final apology")
        .subAgents(refinementLoop, publisher)
        .beforeAgentCallback(LoopRefinementAgent::resetCritiquePass)
        .build();
  }

  public static SequentialAgent create(
      BaseLlm drafterModel, BaseLlm criticModel, BaseLlm publisherModel) {
    return create(drafterModel, criticModel, publisherModel, null);
  }

  private static Maybe<Content> resetCritiquePass(CallbackContext ctx) {
    ctx.state().put(CRITIQUE_PASS_KEY, 0);
    return Maybe.empty();
  }

  private static Optional<Content> incrementCritiquePass(CallbackContext ctx) {
    ctx.state().put(CRITIQUE_PASS_KEY, critiquePass(ctx.state().get(CRITIQUE_PASS_KEY)) + 1);
    return Optional.empty();
  }

  private static Optional<Map<String, Object>> blockPrematureExitLoop(
      InvocationContext invocationContext,
      BaseTool baseTool,
      Map<String, Object> input,
      ToolContext toolContext) {
    if (!"exit_loop".equals(baseTool.name())) {
      return Optional.empty();
    }
    if (critiquePass(toolContext.state().get(CRITIQUE_PASS_KEY)) > 1) {
      return Optional.empty();
    }
    return Optional.of(
        Map.of(
            "status",
            "rejected",
            "reason",
            "First critic pass must output critique bullets; do not exit the loop yet."));
  }

  private static int critiquePass(Object raw) {
    return raw instanceof Number number ? number.intValue() : 0;
  }

  private LoopRefinementAgent() {}
}
