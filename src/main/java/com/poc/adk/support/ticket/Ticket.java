package com.poc.adk.support.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "TICKET")
public class Ticket {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "customer_id", length = 32, nullable = false)
  private String customerId;

  @Column(name = "order_id", length = 32)
  private String orderId;

  @Column(name = "category", length = 32, nullable = false)
  private String category;

  @Column(name = "status", length = 16, nullable = false)
  private String status;

  @Column(name = "resolution_summary", length = 512)
  private String resolutionSummary;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected Ticket() {}

  public String getId() {
    return id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getCategory() {
    return category;
  }

  public String getStatus() {
    return status;
  }

  public String getResolutionSummary() {
    return resolutionSummary;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
