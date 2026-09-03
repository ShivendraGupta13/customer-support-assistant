package com.poc.adk.agents.singleagent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;

/**
 * Bootstrap stub so Dev UI agent discovery works before demo agents exist.
 * Replaced by real demo-single-agent wiring in a later task.
 */
public final class SingleAgent {

  public static final BaseAgent ROOT_AGENT =
      LlmAgent.builder()
          .name("stub-agent")
          .description("Bootstrap stub — verifies Dev UI discovery only")
          .instruction("You are a stub agent used only to verify ADK Dev UI agent loading.")
          .build();

  private SingleAgent() {}
}
