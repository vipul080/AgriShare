-- Enable geo-distance querying (used later by equipment radius search)
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

CREATE TYPE user_role AS ENUM ('OWNER', 'RENTER', 'BOTH');

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120)        NOT NULL,
    email           VARCHAR(160)        NOT NULL UNIQUE,
    phone           VARCHAR(20)         NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    role            user_role           NOT NULL DEFAULT 'BOTH',
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    address         VARCHAR(255),
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
