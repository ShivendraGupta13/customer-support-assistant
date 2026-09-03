package com.poc.adk.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardrailAuditLogRepository extends JpaRepository<GuardrailAuditLog, String> {}
