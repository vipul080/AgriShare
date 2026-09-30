-- JPA maps User.Role with EnumType.STRING (a VARCHAR). A native Postgres ENUM
-- fails Hibernate's `ddl-auto: validate` and rejects plain string binds,
-- so store the role as a constrained VARCHAR instead.
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20) USING role::text;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'BOTH';
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('OWNER', 'RENTER', 'BOTH'));
DROP TYPE user_role;
