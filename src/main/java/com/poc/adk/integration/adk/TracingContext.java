package com.poc.adk.integration.adk;

import io.opentelemetry.api.trace.Tracer;

/**
 * Static bridge so non-Spring agent/tool classes can reach the configured {@link Tracer}. Populated
 * by {@code ObservabilityIntegrationConfig} during context refresh.
 */
public final class TracingContext {

  private static volatile Tracer tracer;

  private TracingContext() {}

  public static void initialize(Tracer tracer) {
    TracingContext.tracer = tracer;
  }

  public static void reset() {
    tracer = null;
  }

  public static Tracer tracer() {
    Tracer current = tracer;
    if (current == null) {
      throw new IllegalStateException("TracingContext not initialized");
    }
    return current;
  }
}
