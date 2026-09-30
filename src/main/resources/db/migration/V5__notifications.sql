-- In-app notifications. Text is not stored: the client translates `type` + `params`
-- into the reader's current language (keys notification.<TYPE>.title/body).
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)     NOT NULL,
    params      JSONB           NOT NULL DEFAULT '{}'::jsonb,
    booking_id  BIGINT          REFERENCES bookings (id) ON DELETE SET NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
