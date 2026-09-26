-- Keyed by the Keycloak subject (sub), not a local sequence: Keycloak owns identity,
-- this table only holds the profile data Keycloak has no business storing.
CREATE TABLE profiles (
    keycloak_sub VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    address VARCHAR(500),
    phone VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
