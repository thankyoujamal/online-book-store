-- Separates how the customer pays (COD or ONLINE) from which gateway took the payment.
-- Usage: mysql -u root online_book_store < sql/migrations/002_payment_gateway.sql

ALTER TABLE orders
    MODIFY payment_method ENUM('COD', 'RAZORPAY', 'ONLINE') NOT NULL DEFAULT 'COD',
    ADD COLUMN payment_gateway VARCHAR(20) NULL AFTER payment_method;

UPDATE orders SET payment_method = 'ONLINE', payment_gateway = 'RAZORPAY' WHERE payment_method = 'RAZORPAY';

ALTER TABLE orders
    MODIFY payment_method ENUM('COD', 'ONLINE') NOT NULL DEFAULT 'COD';
