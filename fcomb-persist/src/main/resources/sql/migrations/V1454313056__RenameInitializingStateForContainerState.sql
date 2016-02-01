UPDATE pg_enum
  SET enumlabel = 'pending'
  WHERE enumtypid = 'container_state'::regtype
    AND enumlabel = 'initializing';
