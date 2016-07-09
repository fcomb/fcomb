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
