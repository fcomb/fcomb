ALTER TABLE dd_image_manifests
  DROP CONSTRAINT dd_image_manifests_image_id_fkey;

ALTER TABLE dd_image_manifests
  ADD CONSTRAINT dd_image_manifests_image_id_fkey
  FOREIGN KEY (image_id)
  REFERENCES dd_images(id)
  ON DELETE CASCADE;
