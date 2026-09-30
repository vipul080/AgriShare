-- Account deletion (Google Play requirement). Bookings and reviews keep pointing at the
-- row for the other farmer's history, so the user is anonymised, not physically deleted.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
