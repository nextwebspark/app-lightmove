-- The strategy's two company lists: the target seeds (companies included in the universe directly)
-- and the off-limits set (companies excluded from all sourcing). The fifth and sixth sections on the
-- 1:1 app_lm_strategy row from V11 — the "seed/off-limits lists" its header promised.
--
-- Two tables, not one table with a list_type flag, for V13's reason: each list saves through its own
-- PUT with replace-list semantics, and one shared ordered collection would force every save to
-- rewrite the other list's rows mid-flush — and the two lists autosave independently, so those
-- rewrites would race. Off-limits will also diverge (it later inherits entries from the client's
-- entity record, which needs provenance columns targets never will).
--
-- (source, source_id) is the row's identity in app_lm_companies — deliberately NOT a foreign key and
-- never its id: the ETL re-mints ids on every pipeline rebuild and upserts on (source, source_id),
-- and rows that vanish upstream are reported, not deleted here. name through hq_country are write-time
-- snapshots resolved server-side from app_lm_companies, so a list still renders after its company
-- vanishes from the universe.
--
-- No unique (strategy_id, source, source_id) index, for the same reason V11 gives: Hibernate rewrites
-- an element collection in place on shrink/reorder, so a non-deferrable unique index can fire on a
-- transient mid-flush state. Duplicates are rejected in the service.

CREATE TABLE app_lm_strategy_target_company (
    strategy_id uuid    NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer NOT NULL,
    source      text    NOT NULL,
    source_id   text    NOT NULL,
    name        text    NOT NULL,
    domain      text,
    slogan      text,
    logo        text,
    hq_city     text,
    hq_country  text,
    PRIMARY KEY (strategy_id, sort_order)
);

CREATE TABLE app_lm_strategy_off_limits_company (
    strategy_id uuid    NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer NOT NULL,
    source      text    NOT NULL,
    source_id   text    NOT NULL,
    name        text    NOT NULL,
    domain      text,
    slogan      text,
    logo        text,
    hq_city     text,
    hq_country  text,
    PRIMARY KEY (strategy_id, sort_order)
);
