package com.poc.adk.commerce.shipment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, String> {

  Optional<Shipment> findByOrderId(String orderId);
}
