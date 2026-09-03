package com.poc.adk.support.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {}
