CREATE TYPE scale_strategy_kind_new AS ENUM ('default', 'every_node', 'emptiest_node', 'high_availability');

ALTER TABLE applications
  ALTER COLUMN ss_kind TYPE scale_strategy_kind_new
  USING (ss_kind::text::scale_strategy_kind_new);

DROP TYPE scale_strategy_kind;

ALTER TYPE scale_strategy_kind_new RENAME TO scale_strategy_kind;
