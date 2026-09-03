package com.poc.adk.integration.config;

import com.poc.adk.guardrails.GuardrailAuditService;
import com.poc.adk.platform.audit.GuardrailAuditLogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GuardrailIntegrationConfig {

  @Bean
  GuardrailAuditService guardrailAuditService(GuardrailAuditLogRepository logs) {
    return new GuardrailAuditService(logs);
  }
}
