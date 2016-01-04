UPDATE pg_enum
  SET enumlabel = 'created'
  WHERE enumtypid = 'application_state'::regtype
    AND enumlabel = 'initializing';
