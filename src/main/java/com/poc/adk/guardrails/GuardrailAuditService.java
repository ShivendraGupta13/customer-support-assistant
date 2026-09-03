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

  private final GuardrailAuditLogRepository logs;

  public GuardrailAuditService(GuardrailAuditLogRepository logs) {
    this.logs = logs;
  }

  public void record(
      String sessionId, String direction, String ruleTriggered, String action) {
    write(sessionId, direction, ruleTriggered, action);
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
