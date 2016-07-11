ALTER TABLE organizations RENAME COLUMN created_by_user_id TO owner_user_id;

ALTER TABLE organization_groups DROP COLUMN created_by_user_id;
