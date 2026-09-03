package com.poc.adk.integration.config;

import com.google.adk.telemetry.Tracing;
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
    // ADK agent/tool/model spans use Tracing's static tracer; point it at Langfuse export.
    Tracing.setTracerForTesting(tracer);
    return tracer;
  }
}
