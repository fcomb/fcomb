CREATE TABLE combs (
  id UUID NOT NULL,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name varchar(255) NOT NULL,
  slug varchar(42) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_combs_user_slug ON combs (user_id, lower(slug));
CREATE UNIQUE INDEX idx_combs_user_name ON combs (user_id, lower(name));
