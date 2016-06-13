CREATE TYPE dd_image_visibility_kind AS ENUM ('private', 'public');

ALTER TABLE dd_images ADD COLUMN visibility_kind dd_image_visibility_kind not null;
ALTER TABLE dd_images ADD COLUMN description text not null default '';
