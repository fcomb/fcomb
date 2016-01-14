CREATE TYPE application_state_new AS ENUM ('created', 'starting', 'running',
  'partly_running', 'stopping', 'stopped', 'redeploying', 'scaling',
  'restarting', 'terminating', 'terminated');
ALTER TABLE applications
  ALTER COLUMN state TYPE application_state_new
    USING (state::text::application_state_new);
DROP TYPE application_state;
ALTER TYPE application_state_new RENAME TO application_state;

CREATE TYPE container_state_new AS ENUM ('initializing', 'created',
  'starting', 'running', 'stopping', 'stopped', 'restarting',
  'terminating', 'terminated');
ALTER TABLE containers
  ALTER COLUMN state TYPE container_state_new
    USING (state::text::container_state_new);
DROP TYPE container_state;
ALTER TYPE container_state_new RENAME TO container_state;
