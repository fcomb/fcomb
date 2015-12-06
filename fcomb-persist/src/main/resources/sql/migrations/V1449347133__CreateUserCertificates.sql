CREATE TYPE user_certificate_kind AS ENUM ('root', 'client');

CREATE TABLE user_certificates (
  user_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind user_certificate_kind NOT NULL,
  certificate bytea NOT NULL,
  key bytea NOT NULL,
  password bytea NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind)
);
