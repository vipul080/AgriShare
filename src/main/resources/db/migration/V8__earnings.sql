-- Ways for the platform to earn without burdening farmers (both switched off by default):
-- 1. an optional service fee added to ONLINE payments only (cash bookings never pay it)
ALTER TABLE bookings ADD COLUMN platform_fee NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (platform_fee >= 0);

-- 2. owners may pay to "boost" a machine to the top of nearby searches for a few days
ALTER TABLE equipment ADD COLUMN featured_until TIMESTAMPTZ;

CREATE TABLE boosts (
    id                  BIGSERIAL PRIMARY KEY,
    equipment_id        BIGINT          NOT NULL REFERENCES equipment (id),
    owner_id            BIGINT          NOT NULL REFERENCES users (id),
    days                INT             NOT NULL CHECK (days > 0),
    amount              NUMERIC(10, 2)  NOT NULL CHECK (amount > 0),
    status              VARCHAR(10)     NOT NULL CHECK (status IN ('PENDING', 'PAID')),
    gateway_order_id    VARCHAR(64)     NOT NULL UNIQUE,
    gateway_payment_id  VARCHAR(64),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at             TIMESTAMPTZ
);

CREATE INDEX idx_boosts_owner ON boosts (owner_id);
