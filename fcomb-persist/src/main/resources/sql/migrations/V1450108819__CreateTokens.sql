CREATE TYPE token_role AS ENUM ('join_cluster', 'api');
CREATE TYPE token_state AS ENUM ('enabled', 'disabled');

CREATE TABLE tokens (
  token varchar(255) NOT NULL,
  role token_role NOT NULL,
  state token_state NOT NULL,
  created_at timestamp with time zone NOT NULL default now(),
  updated_at timestamp with time zone NOT NULL default now(),
  PRIMARY KEY (token)
);

CREATE TABLE user_tokens (
  token varchar(255) NOT NULL,
  user_id integer NOT NULL REFERENCES users(id),
  PRIMARY KEY (token)
) INHERITS (tokens);
