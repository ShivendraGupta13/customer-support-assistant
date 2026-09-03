package com.poc.adk.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

  @Test
  void exportsOtlpHttpProtobufToLangfuseWithBasicAuthAndIngestionHeader() throws Exception {
    CapturedRequest captured = new CapturedRequest();
    CountDownLatch exported = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          captured.method = exchange.getRequestMethod();
          captured.path = exchange.getRequestURI().getPath();
          captured.authorization = exchange.getRequestHeaders().getFirst("Authorization");
          captured.ingestionVersion =
              exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version");
          captured.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
          captured.bodyLength = exchange.getRequestBody().readAllBytes().length;
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
          exported.countDown();
        });
    server.start();

    ObservabilityProperties properties = new ObservabilityProperties();
    properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/api/public/otel");
    properties.setPublicKey("pk-lf-test");
    properties.setSecretKey("sk-lf-test");

    try (OpenTelemetrySdk sdk = ObservabilityConfig.create(properties)) {
      Tracer tracer = sdk.getTracer("com.poc.adk");
      Span span = tracer.spanBuilder("agent.run").startSpan();
      span.end();
      assertThat(sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess())
          .isTrue();
      assertThat(exported.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      server.stop(0);
    }

    String expectedAuth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("pk-lf-test:sk-lf-test".getBytes(StandardCharsets.UTF_8));

    assertThat(captured.method).isEqualTo("POST");
    assertThat(captured.path).isEqualTo("/api/public/otel/v1/traces");
    assertThat(captured.authorization).isEqualTo(expectedAuth);
    assertThat(captured.ingestionVersion).isEqualTo("4");
    assertThat(captured.contentType).contains("protobuf");
    assertThat(captured.bodyLength).isPositive();
  }

  private static final class CapturedRequest {
    volatile String method;
    volatile String path;
    volatile String authorization;
    volatile String ingestionVersion;
    volatile String contentType;
    volatile int bodyLength;
  }
}
