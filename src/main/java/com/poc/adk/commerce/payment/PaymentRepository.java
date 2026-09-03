package com.poc.adk.commerce.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {

  Optional<Payment> findByOrderId(String orderId);
}
