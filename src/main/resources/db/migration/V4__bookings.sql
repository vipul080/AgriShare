CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    equipment_id        BIGINT          NOT NULL REFERENCES equipment (id),
    renter_id           BIGINT          NOT NULL REFERENCES users (id),
    -- whole days, both ends inclusive
    start_date          DATE            NOT NULL,
    end_date            DATE            NOT NULL,
    days                INT             NOT NULL CHECK (days > 0),
    -- price is snapshotted so later price edits don't change existing bookings
    price_per_day       NUMERIC(10, 2)  NOT NULL,
    total_amount        NUMERIC(12, 2)  NOT NULL CHECK (total_amount > 0),
    status              VARCHAR(20)     NOT NULL,
    payment_method      VARCHAR(10)     NOT NULL,
    payment_status      VARCHAR(20)     NOT NULL,
    gateway_order_id    VARCHAR(64) UNIQUE,
    gateway_payment_id  VARCHAR(64),
    note                VARCHAR(500),
    reject_reason       VARCHAR(500),
    -- when the booking reached the owner (payment done / cash chosen); the 96 h expiry counts from here
    requested_at        TIMESTAMPTZ,
    -- optimistic lock: user actions and the expiry scheduler can race on the same row
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_bookings_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_bookings_status CHECK (status IN
        ('AWAITING_PAYMENT', 'REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT chk_bookings_payment_method CHECK (payment_method IN ('ONLINE', 'CASH')),
    CONSTRAINT chk_bookings_payment_status CHECK (payment_status IN
        ('NOT_REQUIRED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'RELEASED', 'REFUNDED'))
);

CREATE INDEX idx_bookings_equipment_dates ON bookings (equipment_id, start_date, end_date);
CREATE INDEX idx_bookings_renter ON bookings (renter_id);
CREATE INDEX idx_bookings_status ON bookings (status);
