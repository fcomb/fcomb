ALTER TABLE dd_image_blobs RENAME COLUMN sha256_digest TO digest;
ALTER TABLE dd_image_manifests RENAME COLUMN sha256_digest TO digest;
