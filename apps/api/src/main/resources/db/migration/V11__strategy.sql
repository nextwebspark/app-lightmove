-- The search strategy behind a project: the parameters Sourcing runs against. One per project,
-- kept off app_lm_project so the list query stays lean — like the position brief, it is a document
-- only one screen reads. This session builds the sector scope; company-size, ownership, location and
-- the seed/off-limits lists attach to this same row in later sessions, as do the universe-lock fields.
--
-- The parent carries only identity and version today. That is deliberate, not premature: the 1:1
-- row is the anchor every later section hangs off, and creating it now means the sector collection
-- never has to be re-keyed. Strategies are seeded lazily on first GET (no template library — an empty
-- scope is the honest starting point), so there is no SQL backfill.

CREATE TABLE app_lm_strategy (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid        NOT NULL UNIQUE REFERENCES app_lm_project (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_strategy_touch BEFORE UPDATE ON app_lm_strategy
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- The selected sectors, across all three kinds, as one ordered replace-list (kind is a field,
-- sort_order spans the whole collection — @OrderColumn owns it). DIRECT are the consultant's chosen
-- core sectors; ADJACENT and INFERRED are AI suggestions the consultant can keep or reject. Rejected
-- suggestions are kept with selected = false so the chip stays visible and re-selectable — dropping
-- them would re-suggest the same rejected label on the next recompute.
--
-- label is a plain-text snapshot of a primary_industry (DIRECT/ADJACENT) or industry_tags value
-- (INFERRED) from app_lm_companies; it is not a foreign key. The company universe is ETL-owned
-- reference data whose ids are re-minted on every rebuild, so a strategy references the value, not a
-- row. An upstream rename orphans the label silently (the chip renders, it matches nothing) — an
-- accepted trade until a canonical sector table exists.
--
-- No unique (strategy_id, kind, label) index: Hibernate rewrites an element collection in place on
-- shrink/reorder, so a non-deferrable unique index can fire on a transient mid-flush state.
-- Duplicates are rejected in the service instead.
CREATE TABLE app_lm_strategy_sector (
    strategy_id uuid         NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    kind        varchar(16)  NOT NULL
        CONSTRAINT app_lm_strategy_sector_kind_chk CHECK (kind IN ('DIRECT', 'ADJACENT', 'INFERRED')),
    label       varchar(160) NOT NULL,
    selected    boolean      NOT NULL DEFAULT true,
    PRIMARY KEY (strategy_id, sort_order)
);
