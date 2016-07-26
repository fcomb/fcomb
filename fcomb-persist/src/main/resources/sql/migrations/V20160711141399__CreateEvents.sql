CREATE TYPE dd_event_kind AS ENUM ('create_repo', 'push_repo');

CREATE TABLE dd_events(
  id SERIAL PRIMARY KEY,
  kind dd_event_kind NOT NULL,
  details_json_blob jsonb NOT NULL,
  created_by integer NOT NULL REFERENCES users(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
