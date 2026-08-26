-- Saved searches get two tiers. V32 gave the mandate one flat list every seat holding PROJECT_EDIT
-- could add to and everyone could see, which is the right default for a scope the team is settling
-- together and the wrong one for a lead's own half-formed attempts at it. A search is now saved
-- PRIVATE (its author only) or SHARED (everyone on the mandate), and the dropdown splits accordingly.
--
-- Everything that exists today was saved into the shared list and stays there, which is what the
-- default does.

ALTER TABLE app_lm_strategy_search
    ADD COLUMN visibility varchar(16) NOT NULL DEFAULT 'SHARED'
        CONSTRAINT app_lm_strategy_search_visibility_chk
        CHECK (visibility IN ('PRIVATE', 'SHARED'));

COMMENT ON COLUMN app_lm_strategy_search.visibility IS
    'PRIVATE: visible and editable only to created_by. SHARED: visible to every seat on the mandate.';

-- V32 described created_by as provenance rather than an ownership fence. That was true of the one
-- list it had; on a PRIVATE row it is now exactly a fence, and the column comment has to say so
-- because V32's own comment cannot be edited.
COMMENT ON COLUMN app_lm_strategy_search.created_by IS
    'Who saved it. Provenance on a SHARED row; on a PRIVATE one, the only person who may read or edit it.';

-- V32's one index was UNIQUE (project_id, lower(name)) across the whole project. That cannot survive
-- private searches: saving "GCC utilities" privately would 409 against a teammate's private row the
-- saver is not allowed to see — a conflict that reports the existence of the very thing private hides.
-- So the namespace splits the same way the visibility does: one shared name per project, one private
-- name per person per project.
DROP INDEX app_lm_strategy_search_name_uk;

CREATE UNIQUE INDEX app_lm_strategy_search_shared_name_uk
    ON app_lm_strategy_search (project_id, lower(name))
    WHERE visibility = 'SHARED';

CREATE UNIQUE INDEX app_lm_strategy_search_private_name_uk
    ON app_lm_strategy_search (project_id, created_by, lower(name))
    WHERE visibility = 'PRIVATE';
