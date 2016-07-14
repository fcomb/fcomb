ALTER TABLE organization_group_users 
  DROP CONSTRAINT organization_group_users_group_id_fkey;

ALTER TABLE organization_group_users 
  ADD CONSTRAINT organization_group_users_group_id_fkey 
  FOREIGN KEY (group_id) 
  REFERENCES organization_groups(id) 
  ON DELETE CASCADE;
