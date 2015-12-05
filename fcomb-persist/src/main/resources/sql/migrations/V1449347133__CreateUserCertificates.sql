CREATE TABLE user_certificates (
  user_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  certificate bytea NOT NULL,
  key bytea NOT NULL,
  password bytea NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id)
);
