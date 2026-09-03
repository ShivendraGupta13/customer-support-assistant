package com.poc.adk.commerce.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CUSTOMER")
public class Customer {

  @Id
  @Column(name = "id", length = 32, nullable = false)
  private String id;

  @Column(name = "name", length = 128, nullable = false)
  private String name;

  @Column(name = "email", length = 256, nullable = false)
  private String email;

  @Column(name = "loyalty_tier", length = 16, nullable = false)
  private String loyaltyTier;

  protected Customer() {}

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getLoyaltyTier() {
    return loyaltyTier;
  }
}
