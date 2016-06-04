CREATE TABLE dd_image_manifest_layers (
  image_manifest_id integer not null references dd_image_manifests(id) ON DELETE CASCADE,
  layer_blob_id uuid not null references dd_image_blobs(id),
  primary key (image_manifest_id, layer_blob_id)
);

CREATE INDEX ON dd_image_manifest_layers (image_manifest_id);

CREATE TABLE dd_image_manifest_tags (
  image_id integer not null references dd_images(id) ON DELETE CASCADE,
  image_manifest_id integer not null references dd_image_manifests(id) ON DELETE CASCADE,
  tag varchar(255) not null,
  primary key (image_id, tag)
);
