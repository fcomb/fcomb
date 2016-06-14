CREATE TABLE organizations (
  id serial primary key,
  name varchar(255) not null,
  created_by_user_id integer not null references users(id),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON organizations (lower(name));

CREATE TYPE acl_role AS ENUM ('admin', 'creator', 'member');

CREATE TABLE organization_groups (
  id serial primary key,
  organization_id integer not null references organizations(id),
  name varchar(255) not null,
  role acl_role not null,
  created_by_user_id integer not null references users(id),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON organization_groups (organization_id, lower(name));

CREATE TABLE organization_group_users (
  group_id integer not null references organization_groups(id),
  user_id integer not null references users(id),
  primary key (group_id, user_id)
);

CREATE TYPE owner_kind AS ENUM ('user', 'organization');

ALTER TABLE dd_images DROP COLUMN user_id;
ALTER TABLE dd_images ADD COLUMN owner_id integer not null;
ALTER TABLE dd_images ADD COLUMN owner_kind owner_kind not null;

CREATE TYPE application_state AS ENUM ('disabled', 'enabled');

CREATE TABLE applications (
  id serial primary key,
  name varchar(255) not null,
  state application_state not null,
  token varchar(255) not null unique,
  owner_id integer not null,
  owner_kind owner_kind not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX ON applications (owner_id, owner_kind, lower(name));

CREATE TYPE acl_action AS ENUM ('read', 'write', 'manage');

CREATE TYPE acl_source_kind AS ENUM ('docker_distribution_image');

CREATE TYPE acl_member_kind AS ENUM ('group', 'user');

CREATE TABLE acl_permissions (
  id serial primary key,
  source_id integer not null,
  source_kind acl_source_kind not null,
  member_id integer not null,
  member_kind acl_member_kind not null,
  action acl_action not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone  
);
