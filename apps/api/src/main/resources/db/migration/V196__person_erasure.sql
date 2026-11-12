-- Erasing a person (#918): an admin's action, and the queue that deletes their files from the bucket.
--
-- PERSON_ERASE is its own action rather than WORKSPACE_MANAGE (docs/candidate-crm.md, goal 6): the one
-- irreversible act on a person, held by ADMIN alone.
--
-- app_lm_storage_deletion holds the bucket keys a committed deletion left behind, drained by StorageDeletionSweeper.
-- A key is written in the same transaction as the rows that named it go, so a rolled-back erasure queues nothing and a
-- committed one cannot lose its files to a crash between the commit and the bucket call. Deliberately global: a key
-- is no tenant's data once its rows are gone, and the sweeper reaches the bucket, not a workspace's rows — so the
-- table carries no workspace_id and sits outside row-level security, as TenantTableGuard reads it.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'PERSON_ERASE', 'Erase a person and everything held about them, permanently')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'WORKSPACE' AND a.name = 'PERSON_ERASE'
WHERE r.scope = 'WORKSPACE' AND r.name = 'ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;

CREATE TABLE app_lm_storage_deletion (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key     varchar(512) NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_storage_deletion_key_uk UNIQUE (storage_key)
);

CREATE INDEX app_lm_storage_deletion_due_idx ON app_lm_storage_deletion (next_attempt_at);

COMMENT ON TABLE app_lm_storage_deletion IS
    'Bucket keys whose rows are gone and whose objects are still to delete. Global: holds keys, no tenant data.';
