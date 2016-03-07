CREATE TYPE docker_distribution_blob_type AS ENUM ('created', 'uploading', 'uploaded');

CREATE TABLE docker_distribution_blobs (
  id uuid primary key,
  image_id integer not null references docker_distribution_images(id),
  digest varchar(64),
  length bigint not null,
  state docker_distribution_blob_type not null,
  created_at timestamp with time zone not null,
  uploaded_at timestamp with time zone
);
