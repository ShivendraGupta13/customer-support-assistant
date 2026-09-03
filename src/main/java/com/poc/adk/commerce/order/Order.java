package com.poc.adk.commerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "`ORDER`")
public class Order {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "customer_id", length = 32, nullable = false)
  private String customerId;

  @Column(name = "product_name", length = 256, nullable = false)
  private String productName;

  @Column(name = "amount", precision = 12, scale = 2, nullable = false)
  private BigDecimal amount;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected Order() {}

  public String getId() {
    return id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public String getProductName() {
    return productName;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
