package com.poc.adk.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.poc.adk.integration.config.ToolIntegrationConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=optional:classpath:do-not-run-schema.sql",
      "spring.sql.init.data-locations=classpath:data.sql"
    })
@Import(ToolIntegrationConfig.class)
class ToolEvalTest {

  @Test
  void orderLookup_returnsDelayedForOrd5001() {
    Map<String, Object> result = OrderLookupTool.orderLookup("ORD-5001");

    assertThat(result.get("status")).isEqualTo("DELAYED");
  }

  @Test
  void orderLookup_returnsEmptyForMissingOrd9999() {
    Map<String, Object> result = OrderLookupTool.orderLookup("ORD-9999");

    assertThat(result).isEmpty();
  }

  @Test
  void fraudSignal_returnsMultipleShippingAddressesOnOrd5002() {
    Map<String, Object> result = FraudSignalTool.fraudSignal("ORD-5002");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> signals = (List<Map<String, Object>>) result.get("signals");
    assertThat(signals).hasSize(1);
    assertThat(signals.getFirst().get("signal_type")).isEqualTo("MULTIPLE_SHIPPING_ADDRESSES");
    assertThat((BigDecimal) signals.getFirst().get("score")).isEqualByComparingTo("0.82");
  }

  @Test
  void paymentHistory_returnsCapturedForOrd5001() {
    Map<String, Object> result = PaymentHistoryTool.paymentHistory("ORD-5001");

    assertThat(result.get("status")).isEqualTo("CAPTURED");
  }

  @Test
  void shipmentTracking_returnsDelayedShp7001ForOrd5001() {
    Map<String, Object> result = ShipmentTrackingTool.shipmentTracking("ORD-5001");

    assertThat(result.get("id")).isEqualTo("SHP-7001");
    assertThat(result.get("status")).isEqualTo("IN_TRANSIT_DELAYED");
    assertThat(result.get("days_delayed")).isEqualTo(6);
  }
}
