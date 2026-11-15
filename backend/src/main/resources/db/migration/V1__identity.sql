CREATE TABLE roles (
  name VARCHAR(20) PRIMARY KEY CHECK (name IN ('EMPLOYEE','TECHNICIAN','ADMIN'))
);
INSERT INTO roles(name) VALUES ('EMPLOYEE'),('TECHNICIAN'),('ADMIN');
CREATE TABLE users (
  id UUID PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role_name VARCHAR(20) NOT NULL REFERENCES roles(name),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CHECK (email = lower(email))
);
CREATE TABLE technician_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id),
  capacity INTEGER NOT NULL DEFAULT 5 CHECK (capacity > 0),
  available BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE auth_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX auth_sessions_user_idx ON auth_sessions(user_id);
