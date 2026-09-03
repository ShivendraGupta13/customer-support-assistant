package com.poc.adk.integration.config;

import com.google.adk.models.BaseLlm;
import com.poc.adk.config.ModelFactory;
import com.poc.adk.config.ModelRoutingProperties;
import com.poc.adk.integration.adk.LlmContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelRoutingProperties.class)
public class LlmIntegrationConfig {

  @Bean
  ModelFactory modelFactory(ModelRoutingProperties properties) {
    return new ModelFactory(properties);
  }

  @Bean
  BaseLlm baseLlm(ModelFactory modelFactory) {
    BaseLlm llm = modelFactory.createLlm();
    LlmContext.initialize(modelFactory, llm);
    return llm;
  }
}
