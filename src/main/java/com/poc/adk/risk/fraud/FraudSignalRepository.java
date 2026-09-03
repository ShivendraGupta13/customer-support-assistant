package com.poc.adk.risk.fraud;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudSignalRepository extends JpaRepository<FraudSignal, String> {

  List<FraudSignal> findByOrderId(String orderId);
}
