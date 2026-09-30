-- Phone is the primary login for farmers; email becomes optional.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(8) NOT NULL DEFAULT 'en';
ALTER TABLE users ADD COLUMN fcm_token VARCHAR(512);
CREATE INDEX idx_users_phone ON users (phone);

CREATE TABLE equipment (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT          NOT NULL REFERENCES users (id),
    name            VARCHAR(120)    NOT NULL,
    category        VARCHAR(30)     NOT NULL,
    description     TEXT,
    price_per_day   NUMERIC(10, 2)  NOT NULL CHECK (price_per_day > 0),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    address         VARCHAR(255),
    image_url       VARCHAR(500),
    available       BOOLEAN         NOT NULL DEFAULT TRUE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_equipment_owner ON equipment (owner_id);
CREATE INDEX idx_equipment_category ON equipment (category);
-- GiST index so earth_box(...) @> ll_to_earth(...) radius filters don't scan the whole table
CREATE INDEX idx_equipment_location ON equipment USING gist (ll_to_earth(latitude, longitude));
