-- Two-way reviews after a COMPLETED booking: the renter rates the owner + machine,
-- the owner rates the renter. One review per booking per side.
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT          NOT NULL REFERENCES bookings (id),
    reviewer_id     BIGINT          NOT NULL REFERENCES users (id),
    reviewee_id     BIGINT          NOT NULL REFERENCES users (id),
    equipment_id    BIGINT          NOT NULL REFERENCES equipment (id),
    reviewer_role   VARCHAR(10)     NOT NULL,
    rating          SMALLINT        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         VARCHAR(1000),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_booking_reviewer UNIQUE (booking_id, reviewer_id),
    CONSTRAINT chk_reviews_role CHECK (reviewer_role IN ('RENTER', 'OWNER'))
);

-- rating aggregates: per machine (renter reviews) and per farmer (any side)
CREATE INDEX idx_reviews_equipment ON reviews (equipment_id) WHERE reviewer_role = 'RENTER';
CREATE INDEX idx_reviews_reviewee ON reviews (reviewee_id);
