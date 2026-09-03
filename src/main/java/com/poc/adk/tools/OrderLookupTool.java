package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.commerce.order.Order;
import com.poc.adk.commerce.order.OrderRepository;
import java.util.Map;

public final class OrderLookupTool {

  private final OrderRepository orders;

  public OrderLookupTool(OrderRepository orders) {
    this.orders = orders;
  }

  @Schema(name = "order_lookup", description = "Look up a Northwind order by id")
  public Map<String, Object> orderLookup(
      @Schema(name = "order_id", description = "Order id, for example ORD-5001") String orderId) {
    return orders.findById(orderId).map(OrderLookupTool::toMap).orElseGet(Map::of);
  }

  private static Map<String, Object> toMap(Order order) {
    return Map.of(
        "id", order.getId(),
        "customer_id", order.getCustomerId(),
        "product_name", order.getProductName(),
        "amount", order.getAmount(),
        "status", order.getStatus(),
        "created_at", order.getCreatedAt().toString());
  }
}
