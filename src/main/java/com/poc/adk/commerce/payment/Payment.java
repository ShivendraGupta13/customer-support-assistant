package com.poc.adk.commerce.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "PAYMENT")
public class Payment {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "order_id", length = 32, nullable = false, unique = true)
  private String orderId;

  @Column(name = "method", length = 32, nullable = false)
  private String method;

  @Column(name = "status", length = 16, nullable = false)
  private String status;

  @Column(name = "amount", precision = 12, scale = 2, nullable = false)
  private BigDecimal amount;

  protected Payment() {}

  public String getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getMethod() {
    return method;
  }

  public String getStatus() {
    return status;
  }

  public BigDecimal getAmount() {
    return amount;
  }
}
