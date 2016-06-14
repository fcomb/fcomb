ALTER TABLE dd_images ADD COLUMN slug varchar(255) not null;

CREATE UNIQUE INDEX ON dd_images (lower(slug));

DROP INDEX dd_images_lower_idx;
