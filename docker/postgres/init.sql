CREATE USER fscbridge_user WITH PASSWORD 'fscbridge_pass';

GRANT ALL PRIVILEGES ON DATABASE fscbridge TO fscbridge_user;
\c fscbridge;

GRANT ALL ON SCHEMA public TO fscbridge_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO fscbridge_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO fscbridge_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
      GRANT ALL ON SEQUENCES TO fscbridge_user;

CREATE TABLE IF NOT EXISTS audit_log (
     id            BIGSERIAL PRIMARY KEY,
     job_id        VARCHAR(255) NOT NULL,
     action         VARCHAR(100) NOT NULL,
     source_record_id VARCHAR(255),
     target_record_id VARCHAR(255),
     object_type      VARCHAR(255),
     success          BOOLEAN,
     error_message    VARCHAR(1000),
     created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_job_id
       ON audit_log(job_id);

CREATE INDEX IF NOT EXISTS idx_audit_job_action
       ON audit_log(created_at DESC);

DO $$
BEGIN
    RAISE NOTICE 'FSC-Bridge database initialized successfully';

END $$;