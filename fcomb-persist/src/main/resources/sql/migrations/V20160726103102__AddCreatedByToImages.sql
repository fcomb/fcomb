ALTER TABLE dd_images ADD COLUMN created_by integer REFERENCES users(id);

UPDATE dd_images SET created_by = owner_id;

ALTER TABLE dd_images ALTER COLUMN created_by SET NOT NULL;
