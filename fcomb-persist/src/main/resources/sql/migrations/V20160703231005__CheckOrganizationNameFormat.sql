ALTER TABLE organizations ADD CONSTRAINT name_format CHECK (name ~ '^[A-Za-z][\w\-\.]*$');
