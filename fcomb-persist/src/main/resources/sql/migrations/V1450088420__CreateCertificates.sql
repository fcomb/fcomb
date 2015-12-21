DROP TABLE user_certificates;
DROP TYPE user_certificate_kind;

CREATE TYPE certificate_kind AS ENUM ('root', 'client');

CREATE TABLE certificates (
  id serial NOT NULL,
  kind certificate_kind NOT NULL,
  certificate bytea NOT NULL,
  key bytea NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  PRIMARY KEY (id)
);

CREATE TABLE user_certificates (
  id integer NOT NULL DEFAULT nextval('certificates_id_seq'),
  user_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (id)
) INHERITS (certificates);

CREATE UNIQUE INDEX ON user_certificates (user_id, kind);
