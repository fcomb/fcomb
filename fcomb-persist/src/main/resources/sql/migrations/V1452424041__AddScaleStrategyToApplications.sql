CREATE TYPE scale_strategy_kind AS ENUM ('every_node', 'emptiest_node', 'high_availability');

DELETE FROM containers CASCADE;
DELETE FROM applications CASCADE;

ALTER TABLE applications ADD COLUMN ss_kind scale_strategy_kind NOT NULL;
ALTER TABLE applications ADD COLUMN ss_number_of_containers int NOT NULL
  CHECK (ss_number_of_containers >= 0);
