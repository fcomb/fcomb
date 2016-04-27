CREATE TABLE docker_distribution_image_manifests (
  id serial primary key,
  image_id integer not null references docker_distribution_images(id),
  sha256_digest varchar(64) not null,
  tags varchar(255)[] not null default array[]::varchar[],
  config_blob_id uuid not null references docker_distribution_image_blobs(id),
  layers_blob_id uuid[] not null default array[]::uuid[],
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX on docker_distribution_image_manifests(image_id, sha256_digest);

CREATE UNIQUE INDEX on docker_distribution_image_manifests(image_id, tags);
