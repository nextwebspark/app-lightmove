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
from collections import defaultdict

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
    universe_by_fold = {}
    for label in (leaf for leaves in taxonomy.values() for leaf in leaves):
        universe_by_fold[fold(label)] = label

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
        if fold(label) != fold(label_by_id[target]):
            aliases[target].add(label)
    if orphans:
        sys.exit(f"V2 industries placed by neither the hierarchy nor CURATED: {orphans}")

    built = {}
    for code in sorted(label_by_id, key=int):
        entry = {"apollo": label_by_id[code]}
        if aliases[code]:
            entry["aliases"] = sorted(aliases[code])
        if code in curated:
            entry["curated"] = True
        built[code] = entry
    built.update({code: {"apollo": label} for code, label in APOLLO_ONLY.items()})

    claimed = {}
    for code, entry in built.items():
        for spelling in [entry["apollo"], *entry.get("aliases", [])]:
            key = fold(spelling)
            if claimed.setdefault(key, code) != code:
                sys.exit(f"'{spelling}' is claimed by both {claimed[key]} and {code}")
    return built


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the map on disk differs from what this rebuilds")
    parser.add_argument("--v2", metavar="PATH",
                        help="read LinkedIn's V2 table from this file instead of downloading it")
    args = parser.parse_args()

    rendered = json.dumps(build(args.v2), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if MAP.read_text() != rendered:
            sys.exit(f"{MAP} is not what this script produces — re-run it without --check")
        print(f"{MAP.name} is up to date")
    else:
        MAP.write_text(rendered)
        print(f"wrote {MAP}")
