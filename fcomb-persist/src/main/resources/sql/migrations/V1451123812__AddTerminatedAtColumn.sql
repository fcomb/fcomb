ALTER TABLE applications ADD COLUMN terminated_at timestamp with time zone;
ALTER TABLE containers ADD COLUMN terminated_at timestamp with time zone;
ALTER TABLE nodes ADD COLUMN terminated_at timestamp with time zone;
