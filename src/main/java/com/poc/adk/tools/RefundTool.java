package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.poc.adk.commerce.order.Order;
import com.poc.adk.commerce.order.OrderRepository;
import com.poc.adk.commerce.payment.Payment;
import com.poc.adk.commerce.payment.PaymentRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** HITL refund tool — threshold check lives in the method body (Playbook §5). */
public final class RefundTool {

  private static final BigDecimal CONFIRMATION_THRESHOLD = new BigDecimal("200");

  private static volatile RefundTool instance;

  private final OrderRepository orders;
  private final PaymentRepository payments;

  public RefundTool(OrderRepository orders, PaymentRepository payments) {
    this.orders = orders;
    this.payments = payments;
    instance = this;
  }

  @Schema(name = "process_refund", description = "Process a refund for a Northwind order")
  public static Map<String, Object> processRefund(
      @Schema(name = "order_id", description = "Order id, for example ORD-5010") String orderId,
      @Schema(name = "toolContext") ToolContext toolContext) {
    RefundTool tool = current();
    return tool.orders
        .findById(orderId)
        .map(order -> tool.refund(order, toolContext))
        .orElseGet(() -> Map.of("status", "not_found", "order_id", orderId));
  }

  private Map<String, Object> refund(Order order, ToolContext toolContext) {
    BigDecimal amount = order.getAmount();
    if (amount.compareTo(CONFIRMATION_THRESHOLD) > 0) {
      var confirmation = toolContext.toolConfirmation();
      if (confirmation.isEmpty()) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", order.getId());
        payload.put("amount", amount);
        payload.put("threshold", CONFIRMATION_THRESHOLD);
        toolContext.requestConfirmation(
            "Refund amount " + amount + " exceeds threshold " + CONFIRMATION_THRESHOLD, payload);
        return Map.of(
            "status", "pending_confirmation",
            "order_id", order.getId(),
            "amount", amount,
            "threshold", CONFIRMATION_THRESHOLD);
      }
      if (!confirmation.get().confirmed()) {
        return Map.of(
            "status", "rejected",
            "order_id", order.getId(),
            "message", "Refund not approved");
      }
    }
    return executeRefund(order);
  }

  private Map<String, Object> executeRefund(Order order) {
    Payment payment = payments.findByOrderId(order.getId()).orElse(null);
    if (payment == null) {
      return Map.of(
          "status", "error",
          "order_id", order.getId(),
          "message", "No payment found for order");
    }
    if ("REFUNDED".equals(payment.getStatus())) {
      return Map.of(
          "status", "already_refunded",
          "order_id", order.getId(),
          "amount", order.getAmount());
    }
    payment.setStatus("REFUNDED");
    payments.save(payment);
    return Map.of(
        "status", "refunded",
        "order_id", order.getId(),
        "amount", order.getAmount(),
        "payment_status", "REFUNDED");
  }

  private static RefundTool current() {
    RefundTool current = instance;
    if (current == null) {
      throw new IllegalStateException("RefundTool not initialized");
    }
    return current;
  }
}
