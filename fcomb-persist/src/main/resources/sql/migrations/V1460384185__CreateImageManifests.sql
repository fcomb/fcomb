CREATE TABLE docker_distribution_image_manifests (
  id serial primary key,
  image_id integer not null references docker_distribution_images(id),
  sha256_digest varchar(64) not null,
  tags varchar(255)[] not null default array[]::varchar[],
  schema_version smallint not null,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone,
  schema_v1_json_blob json not null,
  layers_blob_id uuid[] not null,

  -- schema v2 details
  v2_config_blob_id uuid,
  v2_json_blob json
);

CREATE UNIQUE INDEX on docker_distribution_image_manifests(image_id, sha256_digest);
