CREATE SCHEMA akka;

CREATE TABLE akka.journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  marker VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  created TIMESTAMP NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE TABLE akka.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  snapshot TEXT NOT NULL,
  created BIGINT NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);
