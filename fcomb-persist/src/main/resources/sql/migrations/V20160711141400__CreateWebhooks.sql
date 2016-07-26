CREATE TABLE dd_image_webhooks(
  id serial PRIMARY KEY,
  image_id INTEGER REFERENCES dd_images(id) NOT NULL,
  url VARCHAR(255) NOT NULL
);
