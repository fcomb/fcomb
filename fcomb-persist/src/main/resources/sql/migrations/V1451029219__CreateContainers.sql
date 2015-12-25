CREATE TYPE container_state AS ENUM ('initializing', 'starting', 'running',
  'stopping', 'stopped', 'terminating', 'terminated', 'deleted');

CREATE TABLE containers (
  id serial not null,
  state container_state not null,
  user_id integer not null references users(id),
  application_id integer not null references applications(id),
  node_id integer not null references nodes(id),
  name text not null,
  created_at timestamp with time zone not null default now(),
  primary key (id)
);
