#!/usr/bin/env python3
"""Rebuild data/industry-map.json from LinkedIn's two published industry tables.

    python3 ops/industry-map/build.py            # rewrite the map in place
    python3 ops/industry-map/build.py --check    # fail if the map on disk is not what this produces

Why this exists: the map is 148 entries and 379 aliases, and hand-maintaining it would mean nobody
could ever say where an alias came from. Almost all of it is mechanical.

LinkedIn renamed its V1 industries *in place, at the same numeric ids*, and added ~287 new leaves
under a hierarchy. The company universe publishes V1's vocabulary and Bright Data answers in V2's,
so a V2 label resolves by id where V1 also had one, and otherwise by walking the published hierarchy
up to the nearest ancestor that does. CURATED below is what neither rule can place: 29 V2 industries
with no V1 ancestor, plus the fallout of the one id LinkedIn reused for a different industry (25,
V1's Consumer Goods, now the Manufacturing root).

The universe's own vocabulary is read from sector-taxonomy.json rather than the database: its leaves
are exactly the labels app_lm_apollo_companies carries, and SectorTaxonomyCoverageIntegrationTest is
what keeps that true.
"""
import argparse
import csv
import json
import pathlib
import re
import subprocess
import sys
import unicodedata
from collections import defaultdict, namedtuple

V2_TABLE = ("https://raw.githubusercontent.com/FernandoKGA/linkedin-industry-codes-v2/main/"
            "linkedin_industry_code_v2_all_eng.csv")

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
TAXONOMY = REPO / "apps/api/src/main/resources/data/sector-taxonomy.json"
MAP = REPO / "apps/api/src/main/resources/data/industry-map.json"
V1_TABLE = HERE / "linkedin-v1-industries.csv"

# V2 industries with no V1 ancestor. One reviewable decision each.
CURATED = {
    "2190": "31",   # Accommodation Services             -> hospitality
    "1912": "122",  # Administrative and Support Services -> facilities services
    "1916": "122",  # Office Administration              -> facilities services
    "1931": "123",  # Telephone Call Centers             -> outsourcing/offshoring
    "1938": "43",   # Collection Agencies                -> financial services
    "1999": "69",   # Education                          -> education management
    "2012": "69",   # Secretarial Schools                -> education management
    "2019": "69",   # Cosmetology and Barber Schools     -> education management
    "2025": "69",   # Fine Arts Schools                  -> education management
    "2029": "69",   # Language Schools                   -> education management
    "2018": "105",  # Technical and Vocational Training  -> professional training & coaching
    "2020": "105",  # Flight Training                    -> professional training & coaching
    "2027": "105",  # Sports and Recreation Instruction  -> professional training & coaching
    "201": "63",    # Farming, Ranching, Forestry        -> farming
    "256": "64",    # Ranching and Fisheries             -> ranching
    "298": "61",    # Forestry and Logging               -> paper & forest products
    "1905": "46",   # Holding Companies                  -> investment management
    "332": "57",    # Oil, Gas, and Mining               -> oil & energy
    "1810": "11",   # Professional Services              -> management consulting
    "3242": "135",  # Engineering Services               -> mechanical or industrial engineering
    "3248": "147",  # Robotics Engineering               -> industrial automation
    "3249": "51",   # Surveying and Mapping Services     -> civil engineering
    "3243": "144",  # Services for Renewable Energy      -> renewables & environment
    "1757": "44",   # Real Estate and Equipment Rental   -> real estate
    "1779": "55",   # Equipment Rental Services          -> machinery
    "1798": "55",   # Commercial and Industrial Rental   -> machinery
    "1786": "91",   # Consumer Goods Rental              -> consumer services
    "1594": "96",   # Technology, Information and Media  -> information technology & services
    "3133": "8",    # Media & Telecommunications         -> telecommunications
}

# The ancestor walk places these somewhere defensible but wrong, almost all because V2 reused id 25
# — V1's Consumer Goods — for the Manufacturing ROOT.
OVERRIDE = {
    "598": "19", "615": "19", "616": "19", "622": "19", "625": "19",  # apparel, leather -> apparel & fashion
    "743": "117", "763": "117",                                      # plastics and rubber -> plastics
    "784": "61",                                                     # wood product -> paper & forest products
    "679": "57",                                                     # oil and coal product -> oil & energy
    "1029": "53", "1042": "53",                                      # transport equipment -> automotive
    "840": "56", "807": "56", "849": "56", "852": "56", "861": "56",
    "871": "56", "873": "56", "876": "56", "883": "56", "887": "56",  # metal fabrication -> mining & metals
    "3251": "144",                                                   # climate technology -> renewables & environment
    "562": "142", "564": "142", "2500": "142",                       # breweries, distilleries, wineries
    "1862": "80",                                                    # marketing services -> marketing & advertising
    "2115": "88", "2112": "88",                                      # community, elderly care
}

# The universe publishes this on a handful of rows and LinkedIn never had it, so it has no id.
APOLLO_ONLY = {"apollo:agriculture": "agriculture"}

# What one pass produces: the map the application reads, and the two tables SQL reads.
Vocabulary = namedtuple("Vocabulary", "map industry industry_v2")


def fold(text):
    """The comparison key, character for character what Industries.fold does in Java."""
    without_marks = "".join(c for c in unicodedata.normalize("NFD", text)
                            if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "", without_marks.lower().replace(" and ", " & "))


def v2_table(local):
    """LinkedIn's V2 table, downloaded unless a local copy is named.

    Through curl rather than urllib: a python.org build trusts its own bundled CA store, which on a
    fresh Mac is empty, and the download fails with a certificate error that has nothing to do with
    this script.
    """
    if local:
        return pathlib.Path(local).read_text()
    fetched = subprocess.run(["curl", "-sSL", "--fail", "--max-time", "60", V2_TABLE],
                             capture_output=True, text=True)
    if fetched.returncode != 0:
        sys.exit(f"could not download {V2_TABLE}: {fetched.stderr.strip()}")
    return fetched.stdout


def build(local_v2=None):
    taxonomy = json.loads(TAXONOMY.read_text())
    universe_by_fold, group_by_label = {}, {}
    for group, leaves in taxonomy.items():
        for label in leaves:
            universe_by_fold[fold(label)] = label
            group_by_label[label] = group

    v1 = {row[0]: row[1] for row in csv.reader(V1_TABLE.open())}
    v2_rows = list(csv.reader(v2_table(local_v2).splitlines()))
    v2_id_by_label = {row[1]: row[0] for row in v2_rows}

    label_by_id, missing = {}, []
    for code, label in v1.items():
        hit = universe_by_fold.get(fold(label))
        if hit is None:
            missing.append((code, label))
        else:
            label_by_id[code] = hit
    if missing:
        sys.exit(f"V1 industries the universe's vocabulary does not carry: {missing}")

    aliases, curated, orphans = defaultdict(set), set(), []
    placed = {}
    for code, label in v1.items():
        if fold(label) != fold(label_by_id[code]):
            aliases[code].add(label)

    for code, label, hierarchy, *_ in v2_rows:
        target = OVERRIDE.get(code) or (code if code in label_by_id else None)
        if code in OVERRIDE:
            curated.add(target)
        if target is None:
            for ancestor in reversed(hierarchy.split(" > ")[:-1]):
                ancestor_id = v2_id_by_label.get(ancestor.strip())
                if ancestor_id in label_by_id:
                    target = ancestor_id
                    break
        if target is None:
            target = CURATED.get(code)
            if target is None:
                orphans.append((code, label, hierarchy))
                continue
            curated.add(target)
        # Every V2 row, not only the ones that read differently — app_lm_industry_v2 is what a V2
        # search expands from, so a leaf whose name already matches the universe still needs a row.
        placed[code] = (label, hierarchy, label_by_id[target])
        if fold(label) != fold(label_by_id[target]):
            aliases[target].add(label)
    if orphans:
        sys.exit(f"V2 industries placed by neither the hierarchy nor CURATED: {orphans}")

    built = {}
    for code in sorted(label_by_id, key=int):
        entry = {"apollo": label_by_id[code],
                 "v2Label": placed[code][0] if code in placed else None,
                 "sectorGroup": group_by_label[label_by_id[code]]}
        if aliases[code]:
            entry["aliases"] = sorted(aliases[code])
        if code in curated:
            entry["curated"] = True
        built[code] = entry
    built.update({code: {"apollo": label, "v2Label": None, "sectorGroup": group_by_label[label]}
                  for code, label in APOLLO_ONLY.items()})

    claimed = {}
    for code, entry in built.items():
        for spelling in [entry["apollo"], *entry.get("aliases", [])]:
            key = fold(spelling)
            if claimed.setdefault(key, code) != code:
                sys.exit(f"'{spelling}' is claimed by both {claimed[key]} and {code}")

    industry = []
    for code, entry in built.items():
        numeric = code if code.isdigit() else None
        v2 = placed.get(numeric)
        industry.append((entry["apollo"], numeric, numeric if v2 else None,
                         v2[0] if v2 else None, group_by_label[entry["apollo"]]))
    industry_v2 = [(code, label, hierarchy, v1_label)
                   for code, (label, hierarchy, v1_label) in sorted(placed.items(), key=lambda p: int(p[0]))]
    return Vocabulary(built, industry, industry_v2)


MIGRATION_HEADER = """-- The industry vocabulary, as data.
--
-- data/industry-map.json is read by `Industries` at class-init and by nothing else: a SQL query
-- cannot join a classpath resource. These two tables are that file's projection, so a company row
-- can be joined to its V2 name and its sector group without Java, and so a V2 selection can be
-- resolved to the V1 labels the universe actually files companies under.
--
-- Generated by ops/industry-map/build.py --sql. **The JSON stays the runtime authority** — a record's
-- compact constructor cannot be injected into, so `Industries` can never read a table. An applied
-- migration is immutable; a later correction to the map is a new migration, and
-- IndustryVocabularyIntegrationTest is what catches the two drifting apart in the meantime.
--
-- Deliberately not tenant-scoped, for V49's reason: a published industry taxonomy is not any firm's
-- data, and scoping it would make every workspace carry its own copy of the same 582 rows.

CREATE TABLE app_lm_industry (
    -- The exact string app_lm_apollo_companies carries, and what every other table stores. The key
    -- is the label rather than a code because the label is what every join will be on — and because
    -- one row ('agriculture') is Apollo's own and has no LinkedIn id at all.
    v1_label     text PRIMARY KEY,
    v1_code      integer,
    v2_code      integer,
    v2_label     text,
    -- One of sector-taxonomy.json's 20. NOT NULL because SectorTaxonomyCoverageIntegrationTest
    -- already asserts every live industry belongs to a group.
    sector_group text NOT NULL
);

COMMENT ON TABLE app_lm_industry IS
    'One row per industry the company universe publishes, with its LinkedIn V2 name and its sector group. Generated from data/industry-map.json.';
COMMENT ON COLUMN app_lm_industry.v2_label IS
    'LinkedIn V2''s name for the same id. On an Apollo-sourced company this is V1 renamed, not finer data — the universe never recorded the leaf.';

CREATE TABLE app_lm_industry_v2 (
    v2_code      integer PRIMARY KEY,
    v2_label     text    NOT NULL,
    -- "Technology, Information and Media > ... > Software Development". A branch selection matches on
    -- this prefix; a leaf selection on v2_code.
    v2_hierarchy text    NOT NULL,
    v1_label     text    NOT NULL REFERENCES app_lm_industry (v1_label)
);

COMMENT ON TABLE app_lm_industry_v2 IS
    'One row per LinkedIn V2 industry, resolved to the universe label that covers it. What a V2 search expands from.';

CREATE INDEX app_lm_industry_v2_v1_label_idx ON app_lm_industry_v2 (v1_label);

INSERT INTO app_lm_industry (v1_label, v1_code, v2_code, v2_label, sector_group) VALUES
"""

MIGRATION_MIDDLE = """

INSERT INTO app_lm_industry_v2 (v2_code, v2_label, v2_hierarchy, v1_label) VALUES
"""


def quoted(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def migration(vocabulary):
    industry = ",\n".join(
        "    (%s, %s, %s, %s, %s)" % (quoted(label), code or "NULL", v2_code or "NULL",
                                      quoted(v2_label), quoted(group))
        for label, code, v2_code, v2_label, group in sorted(vocabulary.industry))
    industry_v2 = ",\n".join(
        "    (%s, %s, %s, %s)" % (code, quoted(label), quoted(hierarchy), quoted(v1_label))
        for code, label, hierarchy, v1_label in vocabulary.industry_v2)
    return MIGRATION_HEADER + industry + ";" + MIGRATION_MIDDLE + industry_v2 + ";\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the map on disk differs from what this rebuilds")
    parser.add_argument("--v2", metavar="PATH",
                        help="read LinkedIn's V2 table from this file instead of downloading it")
    parser.add_argument("--sql", metavar="PATH",
                        help="write a migration creating and filling the two lookup tables. Writing "
                             "over an applied migration is never right — point this at a new version.")
    args = parser.parse_args()

    vocabulary = build(args.v2)
    if args.sql:
        target = pathlib.Path(args.sql)
        if target.exists():
            sys.exit(f"{target} already exists — a migration is immutable, choose a new version")
        target.write_text(migration(vocabulary))
        print(f"wrote {target}: {len(vocabulary.industry)} industries, "
              f"{len(vocabulary.industry_v2)} V2 rows")
        sys.exit(0)

    rendered = json.dumps(vocabulary.map, indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if MAP.read_text() != rendered:
            sys.exit(f"{MAP} is not what this script produces — re-run it without --check")
        print(f"{MAP.name} is up to date")
    else:
        MAP.write_text(rendered)
        print(f"wrote {MAP}")
