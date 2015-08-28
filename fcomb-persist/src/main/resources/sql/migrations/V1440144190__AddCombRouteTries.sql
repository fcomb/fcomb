CREATE TABLE comb_route_tries (
  comb_id integer NOT NULL REFERENCES combs(id) ON DELETE CASCADE,
  route_trie bytea NOT NULL,
  route_trie_uid integer NOT NULL,
  PRIMARY KEY (comb_id, route_trie_uid)
);
