package com.poc.adk.memory;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.poc.adk.commerce.customer.CustomerPreference;
import com.poc.adk.commerce.customer.CustomerPreferenceRepository;
import java.util.Map;

public final class CustomerPreferenceTool {

  private static volatile CustomerPreferenceTool instance;

  private final CustomerPreferenceRepository preferences;

  public CustomerPreferenceTool(CustomerPreferenceRepository preferences) {
    this.preferences = preferences;
    instance = this;
  }

  @Schema(
      name = "customer_preference",
      description = "Look up the preferred contact channel for the customer bound to this session")
  public static Map<String, Object> customerPreference(
      @Schema(name = "toolContext") ToolContext toolContext) {
    Object rawId = toolContext.state().get("customer_id");
    if (!(rawId instanceof String customerId)) {
      return Map.of();
    }
    return current()
        .preferences
        .findById(customerId)
        .map(CustomerPreferenceTool::toMap)
        .orElseGet(Map::of);
  }

  private static CustomerPreferenceTool current() {
    CustomerPreferenceTool current = instance;
    if (current == null) {
      throw new IllegalStateException("CustomerPreferenceTool not initialized");
    }
    return current;
  }

  private static Map<String, Object> toMap(CustomerPreference preference) {
    return Map.of(
        "customer_id", preference.getCustomerId(),
        "preferred_contact_channel", preference.getPreferredContactChannel());
  }
}
