CREATE SCHEMA ars_pseudo;

CREATE USER "ars-pseudo-ddl" WITH PASSWORD 'topsecret';
REVOKE ALL PRIVILEGES ON SCHEMA PUBLIC FROM "ars-pseudo-ddl";
GRANT CONNECT ON DATABASE "surveillance-pseudonym" TO "ars-pseudo-ddl";
GRANT ALL PRIVILEGES ON SCHEMA ars_pseudo TO "ars-pseudo-ddl";
ALTER ROLE "ars-pseudo-ddl" SET search_path TO ars_pseudo;

CREATE USER "ars-pseudo-user" WITH PASSWORD 'topsecret';
REVOKE ALL PRIVILEGES ON SCHEMA PUBLIC FROM "ars-pseudo-user";
GRANT CONNECT ON DATABASE "surveillance-pseudonym" TO "ars-pseudo-user";
GRANT USAGE ON SCHEMA ars_pseudo TO "ars-pseudo-user";
ALTER ROLE "ars-pseudo-user" SET search_path TO ars_pseudo;
GRANT EXECUTE ON FUNCTION pg_advisory_xact_lock(bigint) TO "ars-pseudo-user";

CREATE USER "ars-pseudo-purger" WITH PASSWORD 'topsecret';
REVOKE ALL PRIVILEGES ON SCHEMA PUBLIC FROM "ars-pseudo-purger";
GRANT CONNECT ON DATABASE "surveillance-pseudonym" TO "ars-pseudo-purger";
GRANT USAGE ON SCHEMA ars_pseudo TO "ars-pseudo-purger";
ALTER ROLE "ars-pseudo-purger" SET search_path TO ars_pseudo;
GRANT EXECUTE ON FUNCTION pg_advisory_xact_lock(bigint) TO "ars-pseudo-purger";
