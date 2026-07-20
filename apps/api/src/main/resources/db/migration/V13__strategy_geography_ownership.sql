-- The strategy's geography and ownership scopes: the markets and holding structures a project searches
-- within. The third and fourth sections to attach to app_lm_strategy (V11 sectors, V12 company size);
-- the seed/off-limits lists follow onto the same row later. No new parent — both hang off the 1:1
-- app_lm_strategy created in V11.
--
-- Both are *fixed catalogs* like the company-size bands: the enums GeographyMarket / OwnershipStructure
-- own the values, and only the *selected* entries are stored — presence is selection, so there is no
-- `selected` column and no persisted "off" rows. Two tables, not one shared scope table: each section
-- saves through its own PUT with replace-list semantics, and one shared ordered collection would force
-- every save to rewrite the other section's rows mid-flush.
--
-- market / structure hold the enum *name* (SAUDI_ARABIA, PUBLICLY_LISTED …), not a display label. The
-- wire speaks the enum's value (the ISO country code / the token itself); display names live only in
-- the frontend catalog, so UI copy can change without touching data. GeographyMarket's value is the
-- ISO-3166 alpha-2 code because that is app_lm_companies.hq_country verbatim — the join key the
-- sourcing filter will run on when these scopes become filters.
--
-- No unique (strategy_id, value) index, for the same reason V11 gives: Hibernate rewrites an element
-- collection in place on shrink/reorder, so a non-deferrable unique index can fire on a transient
-- mid-flush state. Duplicates are rejected in the service instead — and cannot arise anyway, the
-- values being enums.

CREATE TABLE app_lm_strategy_geography (
    strategy_id uuid        NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    market      varchar(32) NOT NULL,
    PRIMARY KEY (strategy_id, sort_order)
);

CREATE TABLE app_lm_strategy_ownership (
    strategy_id uuid        NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    structure   varchar(48) NOT NULL,
    PRIMARY KEY (strategy_id, sort_order)
);
