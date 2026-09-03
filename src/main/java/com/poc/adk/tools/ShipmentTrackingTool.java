package com.poc.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.poc.adk.commerce.shipment.Shipment;
import com.poc.adk.commerce.shipment.ShipmentRepository;
import java.util.Map;

public final class ShipmentTrackingTool {

  private final ShipmentRepository shipments;

  public ShipmentTrackingTool(ShipmentRepository shipments) {
    this.shipments = shipments;
  }

  @Schema(name = "shipment_tracking", description = "Look up the shipment for a Northwind order")
  public Map<String, Object> shipmentTracking(
      @Schema(name = "order_id", description = "Order id, for example ORD-5001") String orderId) {
    return shipments
        .findByOrderId(orderId)
        .map(ShipmentTrackingTool::toMap)
        .orElseGet(Map::of);
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
