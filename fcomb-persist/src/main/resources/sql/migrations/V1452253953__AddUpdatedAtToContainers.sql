ALTER TABLE containers ADD COLUMN updated_at timestamp with time zone not null default now();
