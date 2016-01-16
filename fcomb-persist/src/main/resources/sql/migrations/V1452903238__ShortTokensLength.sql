UPDATE user_tokens SET token = left(token, 64 + 4) WHERE role = 'api';
UPDATE user_tokens SET token = left(token, 32 + 4) WHERE role = 'join_cluster';
