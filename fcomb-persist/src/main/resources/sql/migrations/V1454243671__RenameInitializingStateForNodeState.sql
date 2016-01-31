UPDATE pg_enum
  SET enumlabel = 'pending'
  WHERE enumtypid = 'node_state'::regtype
    AND enumlabel = 'initializing';
