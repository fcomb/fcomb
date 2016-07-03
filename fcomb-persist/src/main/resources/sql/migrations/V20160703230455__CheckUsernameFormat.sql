ALTER TABLE users ADD CONSTRAINT username_format CHECK (username ~ '^[A-Za-z][\w\-\.]*$');
