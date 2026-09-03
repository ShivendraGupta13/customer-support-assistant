package com.poc.adk.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "GUARDRAIL_AUDIT_LOG")
public class GuardrailAuditLog {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "session_id", length = 64, nullable = false)
  private String sessionId;

  @Column(name = "direction", length = 16, nullable = false)
  private String direction;

  @Column(name = "rule_triggered", length = 128, nullable = false)
  private String ruleTriggered;

  @Column(name = "action", length = 16, nullable = false)
  private String action;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected GuardrailAuditLog() {}

  public String getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getDirection() {
    return direction;
  }

  public String getRuleTriggered() {
    return ruleTriggered;
  }

  public String getAction() {
    return action;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
