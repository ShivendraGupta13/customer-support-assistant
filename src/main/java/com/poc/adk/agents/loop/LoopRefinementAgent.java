package com.poc.adk.agents.loop;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.ExitLoopTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;

import com.poc.adk.guardrails.GuardrailAuditService;

/** Playbook §6 — LoopAgent draft→critique until exit, then publisher emits final draft only. */
public final class LoopRefinementAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new ContextLlm(), new ContextLlm(), null);

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
        .build();
  }

  public static SequentialAgent create(
      BaseLlm drafterModel, BaseLlm criticModel, BaseLlm publisherModel) {
    return create(drafterModel, criticModel, publisherModel, null);
  }

  private LoopRefinementAgent() {}
}
