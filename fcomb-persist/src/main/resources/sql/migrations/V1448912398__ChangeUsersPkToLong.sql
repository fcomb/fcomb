ALTER TABLE combs DROP CONSTRAINT combs_user_id_fkey;

ALTER TABLE users DROP COLUMN id;

ALTER TABLE users ADD COLUMN id serial PRIMARY KEY NOT NULL;