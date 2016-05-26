CREATE TABLE dd_image_manifest_layers (
  image_manifest_id integer not null references docker_distribution_image_manifests(id),
  layer_blob_id uuid not null references docker_distribution_image_blobs(id),
  primary key (image_manifest_id, layer_blob_id)
);

CREATE INDEX ON dd_image_manifest_layers (image_manifest_id);

CREATE TABLE dd_image_manifest_tags (
  image_id integer not null references docker_distribution_images(id),
  image_manifest_id integer not null references docker_distribution_image_manifests(id),
  tag varchar(255) not null,
  primary key (image_id, tag)
);
