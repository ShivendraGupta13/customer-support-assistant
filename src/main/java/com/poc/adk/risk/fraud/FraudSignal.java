package com.poc.adk.risk.fraud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "FRAUD_SIGNAL")
public class FraudSignal {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "order_id", length = 32, nullable = false)
  private String orderId;

  @Column(name = "signal_type", length = 64, nullable = false)
  private String signalType;

  @Column(name = "score", precision = 4, scale = 2, nullable = false)
  private BigDecimal score;

  protected FraudSignal() {}

  public String getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getSignalType() {
    return signalType;
  }

  public BigDecimal getScore() {
    return score;
  }
}
