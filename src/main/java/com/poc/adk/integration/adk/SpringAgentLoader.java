package com.poc.adk.integration.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.web.AgentLoader;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Spring-native {@link AgentLoader} that registers all Spring-managed {@link BaseAgent} beans
 * directly with ADK's web runner and REST endpoints, avoiding reflective class-scanning.
 */
@Service("agentLoader")
@Primary
public class SpringAgentLoader implements AgentLoader {

  private final Map<String, BaseAgent> agentsByName = new LinkedHashMap<>();

  public SpringAgentLoader(List<BaseAgent> agents) {
    for (BaseAgent agent : agents) {
      agentsByName.put(agent.name(), agent);
    }
  }

  @Override
  public ImmutableList<String> listAgents() {
    return ImmutableList.copyOf(agentsByName.keySet());
  }

  @Override
  public BaseAgent loadAgent(String agentName) {
    BaseAgent agent = agentsByName.get(agentName);
    if (agent == null) {
      throw new IllegalArgumentException("Unknown agent: " + agentName);
    }
    return agent;
  }
}
