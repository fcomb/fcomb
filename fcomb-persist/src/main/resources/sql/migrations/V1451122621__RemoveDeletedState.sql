CREATE TYPE node_state_new AS ENUM ('initializing', 'available', 'unreachable', 'upgrading',
  'terminating', 'terminated');
ALTER TABLE nodes
  ALTER COLUMN state TYPE node_state_new
    USING (state::text::node_state_new);
DROP TYPE node_state;
ALTER TYPE node_state_new RENAME TO node_state;

CREATE TYPE application_state_new AS ENUM ('initializing', 'starting', 'running',
  'partly_running', 'stopping', 'stopped', 'deploying', 'scaling', 'terminating',
  'terminated');
ALTER TABLE applications
  ALTER COLUMN state TYPE application_state_new
    USING (state::text::application_state_new);
DROP TYPE application_state;
ALTER TYPE application_state_new RENAME TO application_state;

CREATE TYPE container_state_new AS ENUM ('initializing', 'starting', 'running',
  'stopping', 'stopped', 'terminating', 'terminated');
ALTER TABLE containers
  ALTER COLUMN state TYPE container_state_new
    USING (state::text::container_state_new);
DROP TYPE container_state;
ALTER TYPE container_state_new RENAME TO container_state;
