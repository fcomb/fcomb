CREATE TYPE method_kind AS ENUM ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS');

CREATE TABLE comb_methods (
  id UUID NOT NULL,
  comb_id UUID NOT NULL REFERENCES combs(id) ON DELETE CASCADE,
  kind method_kind NOT NULL,
  uri varchar(2048) NOT NULL,
  endpoint varchar(2048) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_comb_methods_uri_kind ON comb_methods (uri, kind);
