package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.commerce.shipment.Shipment;
import com.poc.adk.commerce.shipment.ShipmentRepository;
import java.util.Map;

public final class ShipmentTrackingTool {

  private static volatile ShipmentTrackingTool instance;

  private final ShipmentRepository shipments;

  public ShipmentTrackingTool(ShipmentRepository shipments) {
    this.shipments = shipments;
    instance = this;
  }

  @Schema(name = "shipment_tracking", description = "Look up the shipment for a Northwind order")
  public static Map<String, Object> shipmentTracking(
      @Schema(name = "order_id", description = "Order id, for example ORD-5001") String orderId) {
    return current()
        .shipments
        .findByOrderId(orderId)
        .map(ShipmentTrackingTool::toMap)
        .orElseGet(Map::of);
  }

  private static ShipmentTrackingTool current() {
    ShipmentTrackingTool current = instance;
    if (current == null) {
      throw new IllegalStateException("ShipmentTrackingTool not initialized");
    }
    return current;
  }

  private static Map<String, Object> toMap(Shipment shipment) {
    return Map.of(
        "id", shipment.getId(),
        "order_id", shipment.getOrderId(),
        "carrier", shipment.getCarrier(),
        "status", shipment.getStatus(),
        "days_delayed", shipment.getDaysDelayed());
  }
}
