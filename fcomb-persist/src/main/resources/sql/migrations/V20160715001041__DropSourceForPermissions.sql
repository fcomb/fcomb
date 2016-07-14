ALTER TABLE acl_permissions RENAME COLUMN source_id to image_id;
ALTER TABLE acl_permissions DROP COLUMN source_kind;

ALTER TABLE acl_permissions
  ADD CONSTRAINT acl_permissions_image_id_fkey 
  FOREIGN KEY (image_id) 
  REFERENCES dd_images(id) 
  ON DELETE CASCADE;

DROP TYPE acl_source_kind;
