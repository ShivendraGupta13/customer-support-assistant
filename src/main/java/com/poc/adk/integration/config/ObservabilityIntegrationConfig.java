package com.poc.adk.integration.config;

import com.poc.adk.config.ObservabilityConfig;
import com.poc.adk.integration.adk.TracingContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityIntegrationConfig {

  @Bean
  Tracer tracer(OpenTelemetrySdk langfuseOpenTelemetrySdk) {
    Tracer tracer = langfuseOpenTelemetrySdk.getTracer(ObservabilityConfig.INSTRUMENTATION_NAME);
    TracingContext.initialize(tracer);
    return tracer;
  }
}
