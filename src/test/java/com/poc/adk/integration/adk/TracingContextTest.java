package com.poc.adk.integration.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TracingContextTest {

  @AfterEach
  void reset() {
    TracingContext.reset();
  }

  @Test
  void tracer_throwsWhenNotInitialized() {
    TracingContext.reset();

    assertThatThrownBy(TracingContext::tracer)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TracingContext not initialized");
  }

  @Test
  void tracer_returnsInitializedTracer() {
    Tracer tracer = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build())
        .build()
        .getTracer("com.poc.adk");

    TracingContext.initialize(tracer);

    assertThat(TracingContext.tracer()).isSameAs(tracer);
  }
}
