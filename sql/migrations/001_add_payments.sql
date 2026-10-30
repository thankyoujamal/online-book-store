-- Adds payment tracking to an existing online_book_store database.
-- Fresh installs get these columns from schema.sql and don't need this file.
-- Usage: mysql -u root online_book_store < sql/migrations/001_add_payments.sql

ALTER TABLE orders
    ADD COLUMN payment_method   ENUM('COD', 'RAZORPAY') NOT NULL DEFAULT 'COD' AFTER status,
    ADD COLUMN payment_status   ENUM('UNPAID', 'PAID')  NOT NULL DEFAULT 'UNPAID' AFTER payment_method,
    ADD COLUMN gateway_order_id VARCHAR(64) NULL AFTER payment_status,
    ADD COLUMN payment_ref      VARCHAR(64) NULL AFTER gateway_order_id,
    ADD COLUMN paid_at          TIMESTAMP NULL AFTER payment_ref,
    ADD CONSTRAINT uq_orders_gateway_order UNIQUE (gateway_order_id);
