CREATE TABLE users (
  id serial PRIMARY KEY,
  email varchar(255) NOT NULL,
  username varchar(255) NOT NULL,
  full_name varchar(255),
  password_hash varchar(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_users_email ON users (lower(email));
CREATE UNIQUE INDEX idx_users_username ON users (lower(username));
