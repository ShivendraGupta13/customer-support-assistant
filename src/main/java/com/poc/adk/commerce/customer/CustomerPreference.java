package com.poc.adk.commerce.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CUSTOMER_PREFERENCE")
public class CustomerPreference {

  @Id
  @Column(name = "customer_id", length = 32, nullable = false)
  private String customerId;

  @Column(name = "preferred_contact_channel", length = 16, nullable = false)
  private String preferredContactChannel;

  protected CustomerPreference() {}

  public String getCustomerId() {
    return customerId;
  }

  public String getPreferredContactChannel() {
    return preferredContactChannel;
  }
}
