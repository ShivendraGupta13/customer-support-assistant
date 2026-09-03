package com.poc.adk.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.poc.adk.config.ObservabilityConfig;
import com.poc.adk.integration.adk.TracingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityIntegrationConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ObservabilityConfig.class, ObservabilityIntegrationConfig.class)
          .withPropertyValues(
              "observability.endpoint=http://127.0.0.1:1/api/public/otel",
              "observability.public-key=pk-lf-test",
              "observability.secret-key=sk-lf-test");

  @AfterEach
  void reset() {
    TracingContext.reset();
  }

  @Test
  void initializesTracingContextDuringContextRefresh() {
    TracingContext.reset();

    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasBean("langfuseOpenTelemetrySdk");
          assertThat(TracingContext.tracer()).isNotNull();
        });
  }

  @Test
  void doesNotCollideWithAdkOpenTelemetrySdkBean() {
    TracingContext.reset();

    contextRunner
        .withUserConfiguration(com.google.adk.web.config.OpenTelemetryConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("openTelemetrySdk");
              assertThat(context).hasBean("langfuseOpenTelemetrySdk");
              assertThat(TracingContext.tracer()).isNotNull();
            });
  }
}
