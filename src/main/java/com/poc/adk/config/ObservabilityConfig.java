package com.poc.adk.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the OpenTelemetry SDK with OTLP/HTTP protobuf export to Langfuse. */
@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfig {

  public static final String INSTRUMENTATION_NAME = "com.poc.adk";

  @Bean(destroyMethod = "close")
  OpenTelemetrySdk langfuseOpenTelemetrySdk(ObservabilityProperties properties) {
    return create(properties);
  }

  public static OpenTelemetrySdk create(ObservabilityProperties properties) {
    String credentials =
        nullToEmpty(properties.getPublicKey()) + ":" + nullToEmpty(properties.getSecretKey());
    String basicAuth =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    OtlpHttpSpanExporter exporter =
        OtlpHttpSpanExporter.builder()
            .setEndpoint(tracesEndpoint(properties.getEndpoint()))
            .addHeader("Authorization", basicAuth)
            .addHeader("x-langfuse-ingestion-version", "4")
            .build();

    Resource resource =
        Resource.getDefault().toBuilder()
            .put(AttributeKey.stringKey("service.name"), "customer-support-assistant")
            .build();

    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build();

    return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
  }

  static String tracesEndpoint(String otlpBase) {
    String base = otlpBase.endsWith("/") ? otlpBase.substring(0, otlpBase.length() - 1) : otlpBase;
    if (base.endsWith("/v1/traces")) {
      return base;
    }
    return base + "/v1/traces";
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
