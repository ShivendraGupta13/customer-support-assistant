-- Playbook §1–8 seed. ORD-9999 must never appear.
-- Full row source of truth; Playbook summary table is a human-readable index.

INSERT INTO CUSTOMER (id, name, email, loyalty_tier) VALUES
    ('CUST-1001', 'Priya Shah', 'priya.shah@example.com', 'GOLD'),
    ('CUST-1002', 'Alex Kim', 'alex.kim@example.com', 'SILVER');

INSERT INTO "ORDER" (id, customer_id, product_name, amount, status, created_at) VALUES
    ('ORD-5001', 'CUST-1001', 'Wireless Headphones', 129.99, 'DELAYED',
     TIMESTAMP '2025-08-01 10:00:00'),
    ('ORD-5010', 'CUST-1002', 'Wireless Headphones', 350.00, 'REFUND_REQUESTED',
     TIMESTAMP '2025-08-10 14:30:00'),
    ('ORD-5002', 'CUST-1002', 'Smart Watch', 249.00, 'PLACED',
     TIMESTAMP '2025-08-12 09:15:00');

INSERT INTO PAYMENT (id, order_id, method, status, amount) VALUES
    ('PAY-9001', 'ORD-5001', 'CREDIT_CARD', 'CAPTURED', 129.99),
    ('PAY-9010', 'ORD-5010', 'CREDIT_CARD', 'CAPTURED', 350.00),
    ('PAY-9002', 'ORD-5002', 'CREDIT_CARD', 'CAPTURED', 249.00);

INSERT INTO SHIPMENT (id, order_id, carrier, status, days_delayed) VALUES
    ('SHP-7001', 'ORD-5001', 'SwiftShip', 'IN_TRANSIT_DELAYED', 6),
    ('SHP-7002', 'ORD-5002', 'SwiftShip', 'IN_TRANSIT', 0);

INSERT INTO TICKET (id, customer_id, order_id, category, status, resolution_summary, created_at)
VALUES
    ('TCK-3001', 'CUST-1001', 'ORD-5001', 'shipping_delay', 'RESOLVED',
     'Carrier weather delay acknowledged; customer notified.',
     TIMESTAMP '2025-08-05 16:00:00');

INSERT INTO FRAUD_SIGNAL (id, order_id, signal_type, score) VALUES
    ('FRD-8002', 'ORD-5002', 'MULTIPLE_SHIPPING_ADDRESSES', 0.82);

INSERT INTO CUSTOMER_PREFERENCE (customer_id, preferred_contact_channel) VALUES
    ('CUST-1001', 'EMAIL'),
    ('CUST-1002', 'SMS');

-- GUARDRAIL_AUDIT_LOG and EVALUATION_RUN intentionally empty at seed.
