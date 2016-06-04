CREATE EXTENSION "uuid-ossp";

CREATE TYPE dd_image_blob_state AS ENUM ('created', 'uploading', 'uploaded');

CREATE TABLE dd_image_blobs (
  id uuid primary key,
  image_id integer not null references dd_images(id),
  state dd_image_blob_state not null,
  sha256_digest varchar(64),
  content_type varchar(512) not null,
  length bigint not null,
  created_at timestamp with time zone not null,
  uploaded_at timestamp with time zone
);

CREATE INDEX ON dd_image_blobs (sha256_digest);
