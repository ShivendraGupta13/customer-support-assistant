package com.poc.adk.agents.rag;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.poc.adk.agents.common.AgentModels;
import com.poc.adk.agents.common.AgentPrompts;
import com.poc.adk.agents.common.ContextLlm;
import com.poc.adk.guardrails.Guardrails;
import com.poc.adk.tools.PolicyRetrievalTool;

import com.poc.adk.guardrails.GuardrailAuditService;

/** Playbook §7 — policy Q&A grounded in {@link PolicyRetrievalTool} with citations. */
public final class RagPolicyAgent {

  public static final BaseAgent ROOT_AGENT =
      create(new ContextLlm(), new PolicyRetrievalTool(null), null);

  public static LlmAgent create(
      BaseLlm model, PolicyRetrievalTool retrievalTool, GuardrailAuditService auditService) {
    var builder =
        LlmAgent.builder()
            .name("demo-rag-policy")
            .description("Policy Q&A with hybrid retrieval and source citations")
            .model(model)
            .instruction(AgentPrompts.load("prompts/demo-rag-policy.v1.md"))
            .tools(FunctionTool.create(retrievalTool, "policyRetrieve"))
            .generateContentConfig(AgentModels.temperatureZero())
            .disallowTransferToParent(true)
            .disallowTransferToPeers(true);
    return Guardrails.apply(builder, auditService).build();
  }

  public static LlmAgent create(BaseLlm model, PolicyRetrievalTool retrievalTool) {
    return create(model, retrievalTool, null);
  }

  private RagPolicyAgent() {}
}
