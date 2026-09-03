package com.poc.adk;

import static org.assertj.core.api.Assertions.assertThat;

import com.poc.adk.commerce.customer.CustomerPreferenceRepository;
import com.poc.adk.commerce.order.Order;
import com.poc.adk.commerce.order.OrderRepository;
import com.poc.adk.risk.fraud.FraudSignal;
import com.poc.adk.risk.fraud.FraudSignalRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=optional:classpath:do-not-run-schema.sql",
      "spring.sql.init.data-locations=classpath:data.sql"
    })
class SeedLoadTest {

  @Autowired private OrderRepository orders;
  @Autowired private CustomerPreferenceRepository preferences;
  @Autowired private FraudSignalRepository fraudSignals;

  @Test
  void playbookSeedIsLoadedFromDataSql() {
    Order delayed = orders.findById("ORD-5001").orElseThrow();
    assertThat(delayed.getStatus()).isEqualTo("DELAYED");

    assertThat(preferences.findById("CUST-1001").orElseThrow().getPreferredContactChannel())
        .isEqualTo("EMAIL");

    assertThat(orders.findById("ORD-5010").orElseThrow().getAmount())
        .isEqualByComparingTo(new BigDecimal("350.00"));

    List<FraudSignal> fraudOn5002 = fraudSignals.findByOrderId("ORD-5002");
    assertThat(fraudOn5002).hasSize(1);
    assertThat(fraudOn5002.getFirst().getScore()).isEqualByComparingTo(new BigDecimal("0.82"));

    assertThat(orders.findById("ORD-9999")).isEmpty();
  }
}
