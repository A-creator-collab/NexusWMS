-- NexusWMS authoritative schema. Idempotent; executed on every start.
-- Hibernate runs with ddl-auto=validate against this file: entities must match it exactly.

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          ENUM('ADMIN','MANAGER','OPERATOR','VIEWER') NOT NULL,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suppliers (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  name              VARCHAR(128) NOT NULL,
  contact_email     VARCHAR(255),
  phone             VARCHAR(32),
  lead_time_days    INT NOT NULL DEFAULT 7,
  reliability_score DECIMAL(4,2) NOT NULL DEFAULT 0.80,
  active            BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_suppliers_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  sku           VARCHAR(64)  NOT NULL,
  name          VARCHAR(160) NOT NULL,
  category      VARCHAR(64),
  description   TEXT,
  unit_price    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  reorder_point INT NOT NULL DEFAULT 0,
  safety_stock  INT NOT NULL DEFAULT 0,
  supplier_id   BIGINT,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_products_sku UNIQUE (sku),
  CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  product_id        BIGINT NOT NULL,
  quantity_on_hand  INT NOT NULL DEFAULT 0,
  quantity_reserved INT NOT NULL DEFAULT 0,
  bin_location      VARCHAR(32),
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_inventory_product UNIQUE (product_id),
  CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT chk_inventory_on_hand  CHECK (quantity_on_hand  >= 0),
  CONSTRAINT chk_inventory_reserved CHECK (quantity_reserved >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  order_number  VARCHAR(32) NOT NULL,
  supplier_id   BIGINT NOT NULL,
  status        ENUM('DRAFT','SUBMITTED','CONFIRMED','RECEIVING','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  expected_date DATE,
  total_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  created_by    BIGINT,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_orders_number UNIQUE (order_number),
  CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT,
  CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  order_id          BIGINT NOT NULL,
  product_id        BIGINT NOT NULL,
  quantity_ordered  INT NOT NULL,
  quantity_received INT NOT NULL DEFAULT 0,
  unit_cost         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE CASCADE,
  CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
  CONSTRAINT chk_items_qty CHECK (quantity_ordered > 0 AND quantity_received >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only ledger: application code never UPDATEs these rows (hence no updated_at).
CREATE TABLE IF NOT EXISTS stock_movements (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  product_id    BIGINT NOT NULL,
  delta         INT NOT NULL,
  reason        ENUM('RECEIVE','SHIP','ADJUST','TRANSFER','RETURN','SHRINK') NOT NULL,
  ref_order_id  BIGINT NULL,
  actor_user_id BIGINT NULL,
  note          TEXT,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_mov_product FOREIGN KEY (product_id)    REFERENCES products (id) ON DELETE RESTRICT,
  CONSTRAINT fk_mov_order   FOREIGN KEY (ref_order_id)  REFERENCES orders (id)   ON DELETE SET NULL,
  CONSTRAINT fk_mov_actor   FOREIGN KEY (actor_user_id) REFERENCES users (id)    ON DELETE SET NULL,
  INDEX idx_mov_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forecasts (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  product_id       BIGINT NOT NULL,
  forecast_date    DATE NOT NULL,
  predicted_demand DECIMAL(12,2) NOT NULL,
  method           VARCHAR(32) NOT NULL,
  confidence       DECIMAL(4,2),
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_forecast_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  INDEX idx_forecast_product_date (product_id, forecast_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  entity_type   VARCHAR(64) NOT NULL,
  entity_id     BIGINT,
  action        VARCHAR(32) NOT NULL,
  actor_user_id BIGINT NULL,
  details       TEXT,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
  INDEX idx_audit_entity (entity_type, entity_id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Added in Part 3: persisted agent proposals awaiting human decision.
CREATE TABLE IF NOT EXISTS approval_requests (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  agent          VARCHAR(48) NOT NULL,
  action         ENUM('CREATE_PO','EXPEDITE_ORDER','ORDER_STATUS_CHANGE') NOT NULL,
  product_id     BIGINT NULL,
  supplier_id    BIGINT NULL,
  order_id       BIGINT NULL,
  quantity       INT NOT NULL DEFAULT 0,
  estimated_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  new_status     VARCHAR(16) NULL,
  rationale      TEXT,
  status         ENUM('PENDING','APPROVED','REJECTED','CHANGES_REQUESTED') NOT NULL DEFAULT 'PENDING',
  dedup_key      VARCHAR(128) NOT NULL,
  decided_by     BIGINT NULL,
  decision_note  TEXT,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_appr_product  FOREIGN KEY (product_id)  REFERENCES products (id)  ON DELETE SET NULL,
  CONSTRAINT fk_appr_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE SET NULL,
  CONSTRAINT fk_appr_order    FOREIGN KEY (order_id)    REFERENCES orders (id)    ON DELETE SET NULL,
  CONSTRAINT fk_appr_decider  FOREIGN KEY (decided_by)  REFERENCES users (id)     ON DELETE SET NULL,
  INDEX idx_appr_status (status),
  INDEX idx_appr_dedup (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
