CREATE EXTENSION btree_gin;

CREATE INDEX ON docker_distribution_image_manifests USING GIN (image_id, tags);
