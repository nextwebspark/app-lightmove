-- A client seat stops seeing a person who said no to being shared with them, withdrew that, or whose processing the
-- firm restricted (#1488, decisions B12 and D2). Staff's own switch (client_visible) stays theirs; the reason lives
-- beside it, kept by the database on every write that can change it, and every client read asks shown_to_client.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN client_hidden_reason varchar(24)
        CONSTRAINT app_lm_project_candidate_client_hidden_chk
            CHECK (client_hidden_reason IN ('PROCESSING_RESTRICTED', 'SHARE_DECLINED', 'SHARE_WITHDRAWN')),
    ADD COLUMN shown_to_client boolean
        GENERATED ALWAYS AS (client_visible AND client_hidden_reason IS NULL) STORED;

-- The one statement of the rule. A share's own status decides; a request asked again, or lapsed, after a no keeps
-- the no until they say yes, so the last answer recorded on it is read (a re-ask turns DECLINED back to REQUESTED).
-- No answer at all is grey: visible, and staff are only warned. Runs as the caller, under their workspace's fence.
CREATE FUNCTION app_lm_client_hidden_reason(p_person_id uuid, p_project_id uuid) RETURNS varchar
    LANGUAGE sql STABLE AS $$
    SELECT CASE
        WHEN (SELECT processing_restricted FROM app_lm_person WHERE id = p_person_id) THEN 'PROCESSING_RESTRICTED'
        ELSE (
            SELECT CASE c.status
                WHEN 'DECLINED' THEN 'SHARE_DECLINED'
                WHEN 'WITHDRAWN' THEN 'SHARE_WITHDRAWN'
                WHEN 'GIVEN' THEN NULL
                ELSE (
                    SELECT CASE e.status WHEN 'DECLINED' THEN 'SHARE_DECLINED' WHEN 'WITHDRAWN' THEN 'SHARE_WITHDRAWN' END
                    FROM app_lm_person_consent_event e
                    WHERE e.consent_id = c.id AND e.action IN ('ANSWERED', 'RECORDED')
                      AND e.status IN ('GIVEN', 'DECLINED', 'WITHDRAWN')
                    ORDER BY e.occurred_at DESC
                    LIMIT 1)
                END
            FROM app_lm_person_consent c
            WHERE c.person_id = p_person_id AND c.project_id = p_project_id AND c.purpose = 'SHARE_WITH_CLIENT')
        END
$$;

-- Someone mapped again after a no, or while restricted, arrives hidden.
CREATE FUNCTION app_lm_project_candidate_fill_client_hidden() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.client_hidden_reason := app_lm_client_hidden_reason(NEW.person_id, NEW.project_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_lm_project_candidate_fill_client_hidden BEFORE INSERT ON app_lm_project_candidate
    FOR EACH ROW EXECUTE FUNCTION app_lm_project_candidate_fill_client_hidden();

-- A restriction set or lifted reaches every position the person is on.
CREATE FUNCTION app_lm_person_refresh_client_hidden() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    UPDATE app_lm_project_candidate pc
    SET client_hidden_reason = app_lm_client_hidden_reason(pc.person_id, pc.project_id)
    WHERE pc.person_id = NEW.id
      AND pc.client_hidden_reason IS DISTINCT FROM app_lm_client_hidden_reason(pc.person_id, pc.project_id);
    RETURN NULL;
END;
$$;

-- Hibernate writes every column on any edit of a person, so the WHEN keeps ordinary edits from touching positions.
CREATE TRIGGER app_lm_person_refresh_client_hidden AFTER UPDATE OF processing_restricted ON app_lm_person
    FOR EACH ROW WHEN (OLD.processing_restricted IS DISTINCT FROM NEW.processing_restricted)
    EXECUTE FUNCTION app_lm_person_refresh_client_hidden();

-- A share answered, withdrawn, recorded by hand, asked again or lapsed reaches the position it is about.
CREATE FUNCTION app_lm_person_consent_refresh_client_hidden() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    UPDATE app_lm_project_candidate pc
    SET client_hidden_reason = app_lm_client_hidden_reason(pc.person_id, pc.project_id)
    WHERE pc.person_id = NEW.person_id AND pc.project_id = NEW.project_id
      AND pc.client_hidden_reason IS DISTINCT FROM app_lm_client_hidden_reason(pc.person_id, pc.project_id);
    RETURN NULL;
END;
$$;

CREATE TRIGGER app_lm_person_consent_refresh_client_hidden AFTER INSERT OR UPDATE OF status
    ON app_lm_person_consent
    FOR EACH ROW WHEN (NEW.purpose = 'SHARE_WITH_CLIENT')
    EXECUTE FUNCTION app_lm_person_consent_refresh_client_hidden();

-- The answers recorded before this migration. Flyway's session reads every workspace (app.rls_maintenance).
UPDATE app_lm_project_candidate pc
SET client_hidden_reason = app_lm_client_hidden_reason(pc.person_id, pc.project_id)
WHERE pc.person_id IN (SELECT id FROM app_lm_person WHERE processing_restricted)
   OR EXISTS (SELECT 1 FROM app_lm_person_consent c
              WHERE c.person_id = pc.person_id AND c.project_id = pc.project_id
                AND c.purpose = 'SHARE_WITH_CLIENT');
