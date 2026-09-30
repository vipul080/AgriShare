-- Runs once, as the Postgres superuser, when the database volume is first created.
-- The app's V1 migration then finds these already present (earthdistance needs superuser).
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
