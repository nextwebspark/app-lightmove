-- A place becomes a point once.
--
-- Nothing in this schema carries a coordinate. A triaged company snapshots a city and a country
-- (V31), an executive carries the same pair (V36), and the Apollo universe itself publishes street,
-- city, state and country and no latitude anywhere (V23). The Companies screen's map view needs a
-- point per row, so the city + country pair is resolved through a geocoding vendor — and the answer
-- is kept here, because "Dubai, United Arab Emirates" resolves to the same point for every mandate
-- that ever asks, and a vendor call per page load would be a bill for re-learning it.
--
-- Deliberately NOT tenant-scoped. Every other table under app_lm_ keys on a workspace or a project;
-- this one holds no fact about any firm, mandate or person — a city's centroid is not client data and
-- carries no PII — so scoping it would only make each tenant pay to resolve the same six countries.
--
-- A miss is stored too (latitude and longitude both null): a city the vendor cannot place would
-- otherwise be re-asked on every read of every mandate that holds it. resolved_at is what lets a
-- cached answer age out — the vendor's terms allow a temporary result to be held only so long unless
-- the account holds its permanent-geocoding entitlement (see lightmove.mapbox.permanent-geocoding).

CREATE TABLE app_lm_geocoded_place (
    id          uuid        PRIMARY KEY,

    -- The normalised "city|country" pair the lookup is keyed on: trimmed, whitespace collapsed,
    -- lower-cased. The display columns beside it keep the spelling the first asker used.
    place_key   text        NOT NULL,
    city        text,
    country     text,

    latitude    double precision,
    longitude   double precision,

    -- CITY when the city itself was placed; COUNTRY when only the country could be, or when the row
    -- had no city to begin with. The map draws both; the panel says which.
    precision   varchar(8)  NOT NULL,

    resolved_at timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_lm_geocoded_place_precision_chk
        CHECK (precision IN ('CITY', 'COUNTRY')),
    -- A point is two numbers or none; a row with one is a bug, not a half-answer.
    CONSTRAINT app_lm_geocoded_place_point_chk
        CHECK ((latitude IS NULL) = (longitude IS NULL))
);

COMMENT ON TABLE app_lm_geocoded_place IS
    'Geocoding cache: one row per distinct city+country ever asked for. Global, not tenant data; a null point is a stored miss.';

-- The one read, and the conflict target for the upsert two concurrent reads race on.
CREATE UNIQUE INDEX app_lm_geocoded_place_key_uk ON app_lm_geocoded_place (place_key);
