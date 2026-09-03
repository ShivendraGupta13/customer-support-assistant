package com.poc.adk.guardrails;

import com.poc.adk.platform.audit.GuardrailAuditLog;
import com.poc.adk.platform.audit.GuardrailAuditLogRepository;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes {@link GuardrailAuditLog} rows. Constructed as a Spring bean; callbacks reach it through
 * the static instance (same pattern as tools).
 */
public final class GuardrailAuditService {

  private static volatile GuardrailAuditService instance;

  private final GuardrailAuditLogRepository logs;

  public GuardrailAuditService(GuardrailAuditLogRepository logs) {
    this.logs = logs;
    instance = this;
  }

  public static void record(
      String sessionId, String direction, String ruleTriggered, String action) {
    current().write(sessionId, direction, ruleTriggered, action);
  }

  private static GuardrailAuditService current() {
    GuardrailAuditService current = instance;
    if (current == null) {
      throw new IllegalStateException("GuardrailAuditService not initialized");
    }
    return current;
  }

  private void write(String sessionId, String direction, String ruleTriggered, String action) {
    logs.save(
        new GuardrailAuditLog(
            UUID.randomUUID().toString().replace("-", ""),
            sessionId,
            direction,
            ruleTriggered,
            action,
            LocalDateTime.now()));
  }
}
