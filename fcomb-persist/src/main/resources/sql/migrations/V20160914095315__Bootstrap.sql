CREATE EXTENSION "uuid-ossp";
CREATE EXTENSION "citext";

CREATE TABLE users (
  id serial PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  email citext NOT NULL,
  full_name varchar(255),
  password_hash varchar(255) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE,
  username citext NOT NULL
);

CREATE UNIQUE INDEX idx_users_email ON users (lower(email));
CREATE UNIQUE INDEX idx_users_username ON users (lower(username));

CREATE TYPE dd_image_visibility_kind AS ENUM ('private', 'public');

CREATE TYPE owner_kind AS ENUM ('user', 'organization');

CREATE TABLE dd_images (
  id serial primary key,
  created_at timestamp with time zone not null,
  created_by integer REFERENCES users(id) not null,
  description text not null,
  name citext not null,
  owner_id integer not null,
  owner_kind owner_kind not null,
  slug citext not null,
  updated_at timestamp with time zone,
  visibility_kind dd_image_visibility_kind not null
);

CREATE UNIQUE INDEX ON dd_images (lower(slug));

CREATE TYPE dd_image_blob_state AS ENUM ('created', 'uploading', 'uploaded');

CREATE TABLE dd_image_blobs (
  id uuid primary key,
  content_type varchar(512) not null,
  created_at timestamp with time zone not null,
  digest varchar(64),
  image_id integer not null references dd_images(id),
  length bigint not null,
  state dd_image_blob_state not null,
  uploaded_at timestamp with time zone
);

CREATE INDEX ON dd_image_blobs (digest);

CREATE TABLE dd_image_manifests (
  id serial primary key,
  created_at timestamp with time zone not null,
  digest varchar(64) not null,
  image_id integer not null references dd_images(id) ON DELETE CASCADE,
  layers_blob_id uuid[] not null,
  length bigint not null,
  schema_v1_json_blob text not null,
  schema_version smallint not null,
  tags varchar(255)[] not null default array[]::varchar[],
  updated_at timestamp with time zone,

  -- schema v2 details
  v2_config_blob_id uuid,
  v2_json_blob text
);

CREATE UNIQUE INDEX on dd_image_manifests(image_id, digest);

CREATE TABLE dd_image_manifest_layers (
  image_manifest_id integer not null references dd_image_manifests(id) ON DELETE CASCADE,
  layer_blob_id uuid not null references dd_image_blobs(id) ON DELETE CASCADE,
  primary key (image_manifest_id, layer_blob_id)
);

CREATE INDEX ON dd_image_manifest_layers (image_manifest_id);

CREATE TABLE dd_image_manifest_tags (
  image_id integer not null references dd_images(id) ON DELETE CASCADE,
  image_manifest_id integer not null references dd_image_manifests(id) ON DELETE CASCADE,
  tag citext not null,
  updated_at timestamp with time zone,
  primary key (image_id, tag)
);

CREATE TABLE dd_image_webhooks(
  id serial PRIMARY KEY,
  image_id INTEGER REFERENCES dd_images(id) NOT NULL,
  url text NOT NULL
);

CREATE UNIQUE INDEX on dd_image_webhooks(image_id, url);

CREATE TYPE dd_event_kind AS ENUM ('create_repo', 'push_repo');

CREATE TABLE dd_events(
  id SERIAL PRIMARY KEY,
  kind dd_event_kind NOT NULL,
  details_json_blob jsonb NOT NULL,
  created_by integer NOT NULL REFERENCES users(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TYPE dd_blob_file_state AS ENUM ('available', 'deleting');

CREATE TABLE dd_blob_files (
  uuid UUID primary key,
  digest varchar(64),
  state dd_blob_file_state not null,
  retry_count smallint not null default 0,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone,
  retried_at timestamp with time zone
);

CREATE INDEX ON dd_blob_files (lower(digest));

CREATE TABLE organizations (
  id serial primary key,
  name citext not null,
  owner_user_id integer not null references users(id),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON organizations (lower(name));

CREATE TYPE acl_role AS ENUM ('admin', 'creator', 'member');

CREATE TABLE organization_groups (
  id serial primary key,
  organization_id integer not null references organizations(id),
  name citext not null,
  role acl_role not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON organization_groups (organization_id, lower(name));

CREATE TABLE organization_group_users (
  group_id integer not null references organization_groups(id) ON DELETE CASCADE,
  user_id integer not null references users(id),
  primary key (group_id, user_id)
);

CREATE TYPE application_state AS ENUM ('disabled', 'enabled');

CREATE TABLE applications (
  id serial primary key,
  name citext not null,
  state application_state not null,
  token varchar(255) not null unique,
  owner_id integer not null,
  owner_kind owner_kind not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON applications (owner_id, owner_kind, lower(name));

CREATE TYPE acl_action AS ENUM ('read', 'write', 'manage');

CREATE TYPE acl_member_kind AS ENUM ('group', 'user');

CREATE TABLE acl_permissions (
  id serial primary key,
  image_id integer not null REFERENCES dd_images(id) ON DELETE CASCADE,
  member_id integer not null,
  member_kind acl_member_kind not null,
  action acl_action not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON acl_permissions (image_id, member_id, member_kind);
