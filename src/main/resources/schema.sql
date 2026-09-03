-- Canonical H2 DDL for Northwind Retail POC.
-- Allowed values: architecture §4.1 / Playbook; enforced with CHECK where listed.

CREATE TABLE CUSTOMER (
    id           VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    email        VARCHAR(256) NOT NULL,
    loyalty_tier VARCHAR(16)  NOT NULL,
    CONSTRAINT ck_customer_loyalty_tier CHECK (loyalty_tier IN ('SILVER', 'GOLD'))
);

-- ORDER is a reserved word in H2; keep the architecture name via quoting.
CREATE TABLE "ORDER" (
    id           VARCHAR(32)    PRIMARY KEY,
    customer_id  VARCHAR(32)    NOT NULL,
    product_name VARCHAR(256)   NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER (id),
    -- Allowed: PLACED, DELAYED, DELIVERED, REFUND_REQUESTED, …
    CONSTRAINT ck_order_status CHECK (status IN (
        'PLACED', 'DELAYED', 'DELIVERED', 'REFUND_REQUESTED'
    ))
);

CREATE TABLE PAYMENT (
    id       VARCHAR(32)    PRIMARY KEY,
    order_id VARCHAR(32)    NOT NULL,
    method   VARCHAR(32)    NOT NULL,
    status   VARCHAR(16)    NOT NULL,
    amount   DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES "ORDER" (id),
    CONSTRAINT uq_payment_order UNIQUE (order_id),
    CONSTRAINT ck_payment_status CHECK (status IN ('CAPTURED', 'REFUNDED', 'PENDING'))
);

CREATE TABLE SHIPMENT (
    id           VARCHAR(32) PRIMARY KEY,
    order_id     VARCHAR(32) NOT NULL,
    carrier      VARCHAR(64) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    days_delayed INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES "ORDER" (id),
    CONSTRAINT uq_shipment_order UNIQUE (order_id),
    CONSTRAINT ck_shipment_status CHECK (status IN (
        'IN_TRANSIT', 'IN_TRANSIT_DELAYED', 'DELIVERED'
    ))
);

CREATE TABLE TICKET (
    id                 VARCHAR(32)  PRIMARY KEY,
    customer_id        VARCHAR(32)  NOT NULL,
    order_id           VARCHAR(32),
    category           VARCHAR(32)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    resolution_summary VARCHAR(512),
    created_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_ticket_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER (id),
    CONSTRAINT fk_ticket_order FOREIGN KEY (order_id) REFERENCES "ORDER" (id),
    CONSTRAINT ck_ticket_category CHECK (category IN (
        'shipping_delay', 'refund', 'fraud', 'account'
    )),
    CONSTRAINT ck_ticket_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE TABLE CUSTOMER_PREFERENCE (
    customer_id               VARCHAR(32) PRIMARY KEY,
    preferred_contact_channel VARCHAR(16) NOT NULL,
    CONSTRAINT fk_preference_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER (id),
    CONSTRAINT ck_preference_channel CHECK (preferred_contact_channel IN (
        'EMAIL', 'SMS', 'PHONE'
    ))
);

CREATE TABLE FRAUD_SIGNAL (
    id          VARCHAR(32)    PRIMARY KEY,
    order_id    VARCHAR(32)    NOT NULL,
    signal_type VARCHAR(64)    NOT NULL,
    score       DECIMAL(4, 2)  NOT NULL,
    CONSTRAINT fk_fraud_order FOREIGN KEY (order_id) REFERENCES "ORDER" (id)
);

-- Standalone audit log (no FK).
CREATE TABLE GUARDRAIL_AUDIT_LOG (
    id             VARCHAR(32)  PRIMARY KEY,
    session_id     VARCHAR(64)  NOT NULL,
    direction      VARCHAR(16)  NOT NULL,
    rule_triggered VARCHAR(128) NOT NULL,
    action         VARCHAR(16)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT ck_guardrail_direction CHECK (direction IN ('INPUT', 'OUTPUT')),
    CONSTRAINT ck_guardrail_action CHECK (action IN ('BLOCKED', 'MASKED', 'ALLOWED'))
);

-- Standalone eval log (no FK).
CREATE TABLE EVALUATION_RUN (
    id        VARCHAR(32)  PRIMARY KEY,
    layer     VARCHAR(32)  NOT NULL,
    test_name VARCHAR(128) NOT NULL,
    status    VARCHAR(32)  NOT NULL,
    run_at    TIMESTAMP    NOT NULL
);
