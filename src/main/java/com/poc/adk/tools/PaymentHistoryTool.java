package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.commerce.payment.Payment;
import com.poc.adk.commerce.payment.PaymentRepository;
import java.util.Map;

public final class PaymentHistoryTool {

  private static volatile PaymentHistoryTool instance;

  private final PaymentRepository payments;

  public PaymentHistoryTool(PaymentRepository payments) {
    this.payments = payments;
    instance = this;
  }

  @Schema(name = "payment_history", description = "Look up the payment for a Northwind order")
  public static Map<String, Object> paymentHistory(
      @Schema(name = "order_id", description = "Order id, for example ORD-5001") String orderId) {
    return current()
        .payments
        .findByOrderId(orderId)
        .map(PaymentHistoryTool::toMap)
        .orElseGet(Map::of);
  }

  private static PaymentHistoryTool current() {
    PaymentHistoryTool current = instance;
    if (current == null) {
      throw new IllegalStateException("PaymentHistoryTool not initialized");
    }
    return current;
  }

  private static Map<String, Object> toMap(Payment payment) {
    return Map.of(
        "id", payment.getId(),
        "order_id", payment.getOrderId(),
        "method", payment.getMethod(),
        "status", payment.getStatus(),
        "amount", payment.getAmount());
  }
}
