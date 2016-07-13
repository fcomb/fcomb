CREATE EXTENSION citext;

ALTER TABLE organizations ALTER COLUMN name TYPE citext USING name::citext;
DROP INDEX organizations_lower_idx;
CREATE UNIQUE INDEX ON organizations (name);

ALTER TABLE dd_images ALTER COLUMN slug TYPE citext USING slug::citext;
ALTER TABLE dd_images ALTER COLUMN name TYPE citext USING name::citext;
DROP INDEX dd_images_lower_idx1;
CREATE UNIQUE INDEX ON dd_images (slug);

ALTER TABLE users ALTER COLUMN email TYPE citext USING email::citext;
ALTER TABLE users ALTER COLUMN username TYPE citext USING username::citext;
DROP INDEX idx_users_email;
DROP INDEX idx_users_username;
CREATE UNIQUE INDEX ON users (email);
CREATE UNIQUE INDEX ON users (username);
