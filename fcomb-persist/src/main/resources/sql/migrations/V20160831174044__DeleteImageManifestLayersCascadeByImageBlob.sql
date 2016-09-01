ALTER TABLE dd_image_manifest_layers
  DROP CONSTRAINT dd_image_manifest_layers_layer_blob_id_fkey;

ALTER TABLE dd_image_manifest_layers
  ADD CONSTRAINT dd_image_manifest_layers_layer_blob_id_fkey 
  FOREIGN KEY (layer_blob_id) 
  REFERENCES dd_image_blobs(id)
  ON DELETE CASCADE;
