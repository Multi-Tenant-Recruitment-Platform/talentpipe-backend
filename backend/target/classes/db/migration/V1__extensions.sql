-- ---------------------------------------------------------------------------
-- V1: PostgreSQL extensions.
--
-- uuid-ossp : uuid_generate_v4() used as the default for UUID primary keys.
-- vector    : pgvector — enabled NOW (unused this sprint) so the AI module's
--             embedding columns land without a disruptive extension migration
--             later. Requires a pgvector-enabled server image
--             (infra/docker-compose.yml uses pgvector/pgvector:pg15).
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
