-- 1. A sample may record which of the representative's customers (e.g. a school) it is for.
-- 2. Sales and samples keep the customer's name, phone and address as they were when the document
--    was saved, so printed invoices never change when a customer record is edited later.

ALTER TABLE sample_issue ADD COLUMN customer_id BIGINT REFERENCES customer (id);
ALTER TABLE sample_issue ADD COLUMN customer_name VARCHAR(150);
ALTER TABLE sample_issue ADD COLUMN customer_phone VARCHAR(30);
ALTER TABLE sample_issue ADD COLUMN customer_address VARCHAR(500);
CREATE INDEX ix_sample_issue_customer ON sample_issue (customer_id, issue_date DESC);

ALTER TABLE sale ADD COLUMN customer_name VARCHAR(150);
ALTER TABLE sale ADD COLUMN customer_phone VARCHAR(30);
ALTER TABLE sale ADD COLUMN customer_address VARCHAR(500);

-- Existing invoices: take the customer's current details once.
UPDATE sale s
SET customer_name    = c.name,
    customer_phone   = c.phone,
    customer_address = c.address
FROM customer c
WHERE c.id = s.customer_id;
