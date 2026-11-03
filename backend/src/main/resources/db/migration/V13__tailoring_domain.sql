-- Tailoring shop. The book-shop tables (publishers, suppliers, categories, purchases, sales, returns,
-- samples, representatives) are KEPT with their data; the application no longer uses them.
--
-- 1. Fabrics are rows of the existing product table (product_type = 'FABRIC').
-- 2. Stock quantities become decimal metres (3 places); the ledger invariants are unchanged.
-- 3. Stock-in is an adjustment reason; order movements are new ledger kinds.
-- 4. Garment types with measurement points, measurement records, orders, payments.
-- 5. Permissions: book-era codes are removed, Orders codes are added.

-- ---------------------------------------------------------------- 1. fabrics on product

ALTER TABLE product DROP CONSTRAINT chk_product_type;
ALTER TABLE product ADD CONSTRAINT chk_product_type CHECK (product_type IN ('BOOK', 'FABRIC'));

ALTER TABLE product ADD COLUMN fabric_code VARCHAR(40);
ALTER TABLE product ADD COLUMN material VARCHAR(100);
ALTER TABLE product ADD COLUMN colour VARCHAR(60);
ALTER TABLE product ADD COLUMN pattern VARCHAR(100);
ALTER TABLE product ADD COLUMN width VARCHAR(30);

-- For fabrics: title = fabric name, purchase_price = cost per metre, selling_price = price per metre.
ALTER TABLE product ADD CONSTRAINT chk_product_fabric_code CHECK (product_type <> 'FABRIC'
    OR (fabric_code IS NOT NULL AND btrim(fabric_code) <> ''));
CREATE UNIQUE INDEX uq_product_fabric_code ON product (lower(fabric_code)) WHERE fabric_code IS NOT NULL;

-- ---------------------------------------------------------------- 2. decimal stock

ALTER TABLE inventory DROP CONSTRAINT chk_inventory_quantity;
ALTER TABLE inventory DROP CONSTRAINT chk_inventory_reorder_level;
ALTER TABLE inventory ALTER COLUMN quantity TYPE NUMERIC(12, 3);
ALTER TABLE inventory ALTER COLUMN reorder_level TYPE NUMERIC(12, 3);
ALTER TABLE inventory ADD CONSTRAINT chk_inventory_quantity CHECK (quantity >= 0);
ALTER TABLE inventory ADD CONSTRAINT chk_inventory_reorder_level CHECK (reorder_level >= 0);

ALTER TABLE stock_transaction DROP CONSTRAINT chk_stock_tx_quantity;
ALTER TABLE stock_transaction DROP CONSTRAINT chk_stock_tx_non_negative;
ALTER TABLE stock_transaction DROP CONSTRAINT chk_stock_tx_arithmetic;
ALTER TABLE stock_transaction ALTER COLUMN quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_transaction ALTER COLUMN previous_quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_transaction ALTER COLUMN resulting_quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_transaction ADD CONSTRAINT chk_stock_tx_quantity CHECK (quantity <> 0);
ALTER TABLE stock_transaction ADD CONSTRAINT chk_stock_tx_non_negative CHECK (previous_quantity >= 0 AND resulting_quantity >= 0);
ALTER TABLE stock_transaction ADD CONSTRAINT chk_stock_tx_arithmetic CHECK (resulting_quantity = previous_quantity + quantity);

ALTER TABLE stock_adjustment DROP CONSTRAINT chk_adjustment_quantity;
ALTER TABLE stock_adjustment DROP CONSTRAINT chk_adjustment_arithmetic;
ALTER TABLE stock_adjustment DROP CONSTRAINT chk_adjustment_non_negative;
ALTER TABLE stock_adjustment DROP CONSTRAINT chk_adjustment_reason;
ALTER TABLE stock_adjustment ALTER COLUMN previous_quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_adjustment ALTER COLUMN adjustment_quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_adjustment ALTER COLUMN resulting_quantity TYPE NUMERIC(12, 3);
ALTER TABLE stock_adjustment ADD CONSTRAINT chk_adjustment_reason CHECK (reason IN
    ('DAMAGED', 'LOST', 'COUNT_CORRECTION', 'FOUND', 'OTHER', 'STOCK_IN'));
ALTER TABLE stock_adjustment ADD CONSTRAINT chk_adjustment_quantity CHECK (adjustment_quantity <> 0);
ALTER TABLE stock_adjustment ADD CONSTRAINT chk_adjustment_arithmetic CHECK (resulting_quantity = previous_quantity + adjustment_quantity);
ALTER TABLE stock_adjustment ADD CONSTRAINT chk_adjustment_non_negative CHECK (previous_quantity >= 0 AND resulting_quantity >= 0);

-- Ledger kinds for orders: fabric issued when an order is taken, returned when it is cancelled.
ALTER TABLE stock_transaction DROP CONSTRAINT chk_stock_tx_type;
ALTER TABLE stock_transaction ADD CONSTRAINT chk_stock_tx_type CHECK (transaction_type IN
    ('PURCHASE', 'SALE', 'SALE_RETURN', 'PURCHASE_RETURN', 'ADJUSTMENT', 'PURCHASE_CANCEL', 'SALE_CANCEL',
     'SAMPLE_ISSUE', 'SAMPLE_RETURN', 'SAMPLE_CANCEL', 'ORDER_ISSUE', 'ORDER_CANCEL'));
ALTER TABLE stock_transaction DROP CONSTRAINT chk_stock_tx_reference_type;
ALTER TABLE stock_transaction ADD CONSTRAINT chk_stock_tx_reference_type CHECK (reference_type IS NULL OR reference_type IN
    ('PURCHASE', 'SALE', 'SALE_RETURN', 'PURCHASE_RETURN', 'ADJUSTMENT', 'SAMPLE_ISSUE', 'SAMPLE_RETURN', 'ORDER'));

-- ---------------------------------------------------------------- 3. customers

-- Customers are people: representative_id and walk_in stay in the table but are no longer used.
-- Walk-in rows are excluded from search and pickers by the application.

-- ---------------------------------------------------------------- 4. garments and measurements

CREATE TABLE garment_type (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    -- Drives the pictorial silhouette on the measurement form.
    shape       VARCHAR(10)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT REFERENCES app_user (id),
    updated_by  BIGINT REFERENCES app_user (id),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_garment_type_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_garment_type_shape CHECK (shape IN ('TOP', 'BOTTOM', 'FULL'))
);

CREATE UNIQUE INDEX uq_garment_type_name ON garment_type (lower(name));

-- Measurements are in inches.
CREATE TABLE garment_measurement_point (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    garment_type_id BIGINT       NOT NULL REFERENCES garment_type (id),
    label           VARCHAR(80)  NOT NULL,
    sort_order      INTEGER      NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES app_user (id),
    updated_by      BIGINT REFERENCES app_user (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_garment_point_label UNIQUE (garment_type_id, label),
    CONSTRAINT chk_garment_point_label CHECK (btrim(label) <> '')
);

CREATE INDEX ix_garment_point_type ON garment_measurement_point (garment_type_id, sort_order);

-- A measurement record is never edited; a new one is taken instead.
CREATE TABLE measurement_record (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_id     BIGINT       NOT NULL REFERENCES customer (id),
    garment_type_id BIGINT       NOT NULL REFERENCES garment_type (id),
    taken_on        DATE         NOT NULL,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES app_user (id),
    updated_by      BIGINT REFERENCES app_user (id),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_measurement_record_customer ON measurement_record (customer_id, taken_on DESC);
CREATE INDEX ix_measurement_record_type ON measurement_record (garment_type_id);

CREATE TABLE measurement_value (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    record_id       BIGINT        NOT NULL REFERENCES measurement_record (id) ON DELETE CASCADE,
    point_id        BIGINT        NOT NULL REFERENCES garment_measurement_point (id),
    -- Snapshots: the label and order as they were when the record was taken.
    point_label     VARCHAR(80)   NOT NULL,
    sort_order      INTEGER       NOT NULL,
    value           NUMERIC(6, 2) NOT NULL,
    CONSTRAINT uq_measurement_value_point UNIQUE (record_id, point_id),
    CONSTRAINT chk_measurement_value CHECK (value > 0)
);

-- ---------------------------------------------------------------- 5. orders

-- Table is "orders": "order" is a reserved word in SQL.
CREATE TABLE orders (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_number  VARCHAR(20)   NOT NULL,
    customer_id   BIGINT        NOT NULL REFERENCES customer (id),
    status        VARCHAR(20)   NOT NULL,
    order_date    DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    trial_date    DATE,
    delivery_date DATE,
    notes         VARCHAR(500),
    -- Sum of line totals, computed by the server only.
    total         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(255),
    cancelled_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    BIGINT REFERENCES app_user (id),
    updated_by    BIGINT REFERENCES app_user (id),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT chk_orders_status CHECK (status IN
        ('RECEIVED', 'CUTTING', 'STITCHING', 'TRIAL', 'READY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_total CHECK (total >= 0),
    CONSTRAINT chk_orders_due_date CHECK (due_date >= order_date),
    CONSTRAINT chk_orders_cancel CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX ix_orders_due_date ON orders (due_date);
CREATE INDEX ix_orders_status ON orders (status);
CREATE INDEX ix_orders_customer ON orders (customer_id, order_date DESC);

CREATE TABLE order_line (
    id                     BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id               BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_no                INTEGER       NOT NULL,
    garment_type_id        BIGINT        NOT NULL REFERENCES garment_type (id),
    fabric_source          VARCHAR(10)   NOT NULL,
    fabric_product_id      BIGINT REFERENCES product (id),
    -- Snapshots taken when the order is saved; later fabric price changes never alter the order.
    fabric_name            VARCHAR(300),
    fabric_metres          NUMERIC(12, 3),
    customer_fabric_note   VARCHAR(200),
    fabric_price_per_metre NUMERIC(12, 2),
    fabric_amount          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    stitching_charge       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    line_total             NUMERIC(14, 2) NOT NULL,
    -- Reference only: the values were copied into order_line_measurement.
    measurement_record_id  BIGINT REFERENCES measurement_record (id),
    notes                  VARCHAR(255),
    CONSTRAINT uq_order_line_no UNIQUE (order_id, line_no),
    CONSTRAINT chk_order_line_source CHECK (fabric_source IN ('STOCK', 'CUSTOMER')),
    CONSTRAINT chk_order_line_stock CHECK (fabric_source <> 'STOCK'
        OR (fabric_product_id IS NOT NULL AND fabric_metres IS NOT NULL AND fabric_metres > 0
            AND fabric_price_per_metre IS NOT NULL)),
    CONSTRAINT chk_order_line_customer CHECK (fabric_source <> 'CUSTOMER'
        OR (fabric_product_id IS NULL AND fabric_metres IS NULL)),
    CONSTRAINT chk_order_line_amounts CHECK (fabric_amount >= 0 AND stitching_charge >= 0
        AND line_total = fabric_amount + stitching_charge),
    CONSTRAINT chk_order_line_fabric_amount CHECK (fabric_source = 'CUSTOMER'
        OR fabric_amount = round(fabric_metres * fabric_price_per_metre, 2))
);

CREATE INDEX ix_order_line_fabric ON order_line (fabric_product_id);
CREATE INDEX ix_order_line_measurement ON order_line (measurement_record_id);

CREATE TABLE order_line_measurement (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_line_id BIGINT        NOT NULL REFERENCES order_line (id) ON DELETE CASCADE,
    point_label   VARCHAR(80)   NOT NULL,
    value         NUMERIC(6, 2) NOT NULL,
    sort_order    INTEGER       NOT NULL,
    CONSTRAINT chk_order_line_measurement_value CHECK (value > 0)
);

CREATE INDEX ix_order_line_measurement_line ON order_line_measurement (order_line_id);

-- Append-only: every status change adds a row.
CREATE TABLE order_status_history (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES orders (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by  BIGINT REFERENCES app_user (id),
    note        VARCHAR(255),
    CONSTRAINT chk_order_history_to CHECK (to_status IN
        ('RECEIVED', 'CUTTING', 'STITCHING', 'TRIAL', 'READY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_order_history_from CHECK (from_status IS NULL OR from_status IN
        ('RECEIVED', 'CUTTING', 'STITCHING', 'TRIAL', 'READY', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX ix_order_history_order ON order_status_history (order_id, id);

-- Append-only: payments are never edited or deleted; a mistake is corrected by a reason in the notes.
CREATE TABLE order_payment (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES orders (id),
    kind        VARCHAR(10)   NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL,
    paid_on     DATE          NOT NULL,
    method      VARCHAR(10)   NOT NULL,
    note        VARCHAR(255),
    recorded_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recorded_by BIGINT REFERENCES app_user (id),
    CONSTRAINT chk_order_payment_kind CHECK (kind IN ('ADVANCE', 'PAYMENT')),
    CONSTRAINT chk_order_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_order_payment_method CHECK (method IN ('CASH', 'BANK', 'CARD', 'OTHER'))
);

CREATE INDEX ix_order_payment_order ON order_payment (order_id, id);

-- ---------------------------------------------------------------- 6. permissions

-- Book-era permissions: their role links go first (role_permission references permission).
DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE code IN
    ('PURCHASE_VIEW', 'PURCHASE_CREATE', 'PURCHASE_CANCEL', 'SALE_VIEW', 'SALE_CREATE', 'SALE_CANCEL',
     'RETURN_CREATE', 'SAMPLE_VIEW', 'SAMPLE_MANAGE'));
DELETE FROM permission WHERE code IN
    ('PURCHASE_VIEW', 'PURCHASE_CREATE', 'PURCHASE_CANCEL', 'SALE_VIEW', 'SALE_CREATE', 'SALE_CANCEL',
     'RETURN_CREATE', 'SAMPLE_VIEW', 'SAMPLE_MANAGE');

UPDATE permission SET description = 'View fabrics, garment types, measurements and customers'
    WHERE code = 'MASTER_DATA_VIEW';
UPDATE permission SET description = 'Create, edit and deactivate fabrics, garment types, measurements and customers'
    WHERE code = 'MASTER_DATA_MANAGE';
UPDATE permission SET description = 'View fabric stock and stock movements'
    WHERE code = 'INVENTORY_VIEW';
UPDATE permission SET description = 'Record fabric stock-in and stock adjustments'
    WHERE code = 'STOCK_ADJUST';
UPDATE permission SET description = 'View reports'
    WHERE code = 'REPORT_VIEW';

INSERT INTO permission (code, module, description) VALUES
    ('ORDER_VIEW',   'Orders', 'View orders, the order calendar and order details'),
    ('ORDER_MANAGE', 'Orders', 'Take, change, progress and cancel orders and record payments');

-- ADMIN gets every permission; NORMAL_USER gets the day-to-day set.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.name = 'NORMAL_USER'
  AND p.code IN ('DASHBOARD_VIEW', 'MASTER_DATA_VIEW', 'INVENTORY_VIEW', 'ORDER_VIEW', 'ORDER_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
