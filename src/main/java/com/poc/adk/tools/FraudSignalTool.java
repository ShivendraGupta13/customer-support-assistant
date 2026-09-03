package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.risk.fraud.FraudSignal;
import com.poc.adk.risk.fraud.FraudSignalRepository;
import java.util.List;
import java.util.Map;

public final class FraudSignalTool {

  private final FraudSignalRepository fraudSignals;

  public FraudSignalTool(FraudSignalRepository fraudSignals) {
    this.fraudSignals = fraudSignals;
  }

  @Schema(name = "fraud_signal", description = "Look up fraud signals for a Northwind order")
  public Map<String, Object> fraudSignal(
      @Schema(name = "order_id", description = "Order id, for example ORD-5002") String orderId) {
    List<Map<String, Object>> signals =
        fraudSignals.findByOrderId(orderId).stream()
            .map(FraudSignalTool::toMap)
            .toList();
    return Map.of("order_id", orderId, "signals", signals);
  }

  private static Map<String, Object> toMap(FraudSignal signal) {
    return Map.of(
        "id", signal.getId(),
        "order_id", signal.getOrderId(),
        "signal_type", signal.getSignalType(),
        "score", signal.getScore());
  }
}
