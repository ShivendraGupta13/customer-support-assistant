package com.poc.adk.integration.config;

import com.poc.adk.commerce.customer.CustomerPreferenceRepository;
import com.poc.adk.commerce.customer.CustomerRepository;
import com.poc.adk.commerce.order.OrderRepository;
import com.poc.adk.commerce.payment.PaymentRepository;
import com.poc.adk.commerce.shipment.ShipmentRepository;
import com.poc.adk.memory.CustomerPreferenceTool;
import com.poc.adk.risk.fraud.FraudSignalRepository;
import com.poc.adk.tools.FraudSignalTool;
import com.poc.adk.tools.OrderLookupTool;
import com.poc.adk.tools.PaymentHistoryTool;
import com.poc.adk.tools.RefundTool;
import com.poc.adk.tools.ShipmentTrackingTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolIntegrationConfig {

  @Bean
  OrderLookupTool orderLookupTool(OrderRepository orders) {
    return new OrderLookupTool(orders);
  }

  @Bean
  PaymentHistoryTool paymentHistoryTool(PaymentRepository payments) {
    return new PaymentHistoryTool(payments);
  }

  @Bean
  ShipmentTrackingTool shipmentTrackingTool(ShipmentRepository shipments) {
    return new ShipmentTrackingTool(shipments);
  }

  @Bean
  FraudSignalTool fraudSignalTool(FraudSignalRepository fraudSignals) {
    return new FraudSignalTool(fraudSignals);
  }

  @Bean
  CustomerPreferenceTool customerPreferenceTool(
      CustomerPreferenceRepository preferences, CustomerRepository customers) {
    return new CustomerPreferenceTool(preferences, customers);
  }

  @Bean
  RefundTool refundTool(OrderRepository orders, PaymentRepository payments) {
    return new RefundTool(orders, payments);
  }
}
