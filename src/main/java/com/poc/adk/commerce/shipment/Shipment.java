package com.poc.adk.commerce.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "SHIPMENT")
public class Shipment {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "order_id", length = 32, nullable = false, unique = true)
  private String orderId;

  @Column(name = "carrier", length = 64, nullable = false)
  private String carrier;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @Column(name = "days_delayed", nullable = false)
  private int daysDelayed;

  protected Shipment() {}

  public String getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getCarrier() {
    return carrier;
  }

  public String getStatus() {
    return status;
  }

  public int getDaysDelayed() {
    return daysDelayed;
  }
}
