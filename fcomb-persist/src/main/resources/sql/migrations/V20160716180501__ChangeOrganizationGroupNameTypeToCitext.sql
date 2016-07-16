ALTER TABLE organization_groups ALTER COLUMN name TYPE citext USING name::citext;
DROP INDEX organization_groups_organization_id_lower_idx;
CREATE UNIQUE INDEX ON organization_groups (organization_id, name);
