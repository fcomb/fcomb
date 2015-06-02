CREATE EXTENSION "uuid-ossp";

CREATE TABLE users (
   id UUID NOT NULL DEFAULT uuid_generate_v4(),
   username varchar(255) NOT NULL,
   email varchar(255),
   full_name varchar(255),
   salt varchar(255) NOT NULL,
   password_hash varchar(255) NOT NULL,
   created_at TIMESTAMP NOT NULL DEFAULT now(),
   updated_at TIMESTAMP NOT NULL DEFAULT now(),
   PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_users_email ON users (lower(email));
CREATE UNIQUE INDEX idx_users_username ON users (lower(username));
