package com.poc.adk.platform.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardrailAuditLogRepository extends JpaRepository<GuardrailAuditLog, String> {

  List<GuardrailAuditLog> findBySessionId(String sessionId);
}
