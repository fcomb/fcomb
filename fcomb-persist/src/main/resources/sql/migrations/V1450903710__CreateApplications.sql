CREATE TYPE application_state AS ENUM ('initializing', 'running', 'stopping', 'stopped', 'pausing', 'paused', 'destroying');

CREATE TABLE applications (
  id serial NOT NULL,
  user_id integer NOT NULL REFERENCES users(id),
  state application_state NOT NULL,
  name varchar(255) NOT NULL,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),

  -- docker image columns
  di_name varchar(512) NOT NULL,
  di_tag varchar(255),
  di_registry text,

  -- docker deploy options
  ddo_ports json NOT NULL DEFAULT '[]'::json,
  ddo_is_auto_restart boolean NOT NULL DEFAULT false,
  ddo_is_auto_destroy boolean NOT NULL DEFAULT false,
  ddo_is_privileged boolean NOT NULL DEFAULT false,
  ddo_command text,
  ddo_entrypoint text,
  ddo_memory_limit bigint,
  ddo_cpu_shares bigint,
  PRIMARY KEY (id)
);
