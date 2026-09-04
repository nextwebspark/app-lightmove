-- The profile photo enrichment downloaded for a captured executive — 1:1 with
-- app_lm_project_candidate, same shape as V16's position document. Bytes are stored rather than the
-- provider's URL because those links carry expiring signatures: a stored URL is a broken avatar a
-- few weeks later. Its own table so the roster reads never drag image bytes; the FK cascades because
-- a photo is meaningless without its person (unmapping a candidate from a company never touches it).

CREATE TABLE app_lm_candidate_photo (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id  uuid        NOT NULL UNIQUE REFERENCES app_lm_project_candidate (id) ON DELETE CASCADE,
    content       bytea       NOT NULL,
    content_type  varchar(120) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_candidate_photo_touch BEFORE UPDATE ON app_lm_candidate_photo
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
