ALTER TABLE docker_distribution_images RENAME TO dd_images;
ALTER TABLE docker_distribution_image_blobs RENAME TO dd_image_blobs;
ALTER TABLE docker_distribution_image_manifests RENAME TO dd_image_manifests;

ALTER TYPE docker_distribution_image_blob_state RENAME TO dd_image_blob_state;
