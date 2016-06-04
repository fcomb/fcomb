CREATE EXTENSION hstore;

CREATE AGGREGATE array_agg_custom(anyarray) (
  SFUNC = array_cat,
  STYPE = anyarray
);
