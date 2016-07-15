CREATE TYPE dd_image_event_kind AS ENUM ('upserted');

CREATE TABLE dd_image_events(
  id SERIAL PRIMARY KEY,
  image_manifest_id INT REFERENCES dd_image_manifests(id) NOT NULL,
  kind dd_image_event_kind NOT NULL,
  details_json_blob TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE dd_image_webhooks(
  id serial PRIMARY KEY,
  image_id INTEGER REFERENCES dd_images(id) NOT NULL,
  url VARCHAR(255) NOT NULL
);