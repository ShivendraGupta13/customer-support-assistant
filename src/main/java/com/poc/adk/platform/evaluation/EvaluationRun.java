package com.poc.adk.platform.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "EVALUATION_RUN")
public class EvaluationRun {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "layer", length = 32, nullable = false)
  private String layer;

  @Column(name = "test_name", length = 128, nullable = false)
  private String testName;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @Column(name = "run_at", nullable = false)
  private LocalDateTime runAt;

  protected EvaluationRun() {}

  public String getId() {
    return id;
  }

  public String getLayer() {
    return layer;
  }

  public String getTestName() {
    return testName;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getRunAt() {
    return runAt;
  }
}
