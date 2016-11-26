UPDATE pg_enum
  SET enumlabel = 'user'
  WHERE enumtypid = 'user_role'::regtype
    AND enumlabel = 'developer';
