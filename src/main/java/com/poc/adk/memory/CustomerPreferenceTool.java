package com.poc.adk.memory;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.poc.adk.commerce.customer.Customer;
import com.poc.adk.commerce.customer.CustomerPreference;
import com.poc.adk.commerce.customer.CustomerPreferenceRepository;
import com.poc.adk.commerce.customer.CustomerRepository;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CustomerPreferenceTool {

  private final CustomerPreferenceRepository preferences;
  private final CustomerRepository customers;

  public CustomerPreferenceTool(
      CustomerPreferenceRepository preferences, CustomerRepository customers) {
    this.preferences = preferences;
    this.customers = customers;
  }

  @Schema(
      name = "customer_preference",
      description = "Look up the preferred contact channel for the customer bound to this session")
  public Map<String, Object> customerPreference(
      @Schema(name = "toolContext") ToolContext toolContext) {
    Object rawId = toolContext.state().get("customer_id");
    if (!(rawId instanceof String customerId)) {
      return Map.of();
    }
    return preferences
        .findById(customerId)
        .map(preference -> toMap(preference, customers.findById(customerId).orElse(null)))
        .orElseGet(Map::of);
  }

  private static Map<String, Object> toMap(CustomerPreference preference, Customer customer) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customer_id", preference.getCustomerId());
    result.put("preferred_contact_channel", preference.getPreferredContactChannel());
    if (customer != null) {
      result.put("name", customer.getName());
      result.put("email", customer.getEmail());
    }
    return Map.copyOf(result);
  }
}
