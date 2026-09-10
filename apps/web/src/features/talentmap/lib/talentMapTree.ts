import { SENIORITY_TOKENS } from "../../../lib/seniority";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../../triage/api/types";
import type { MapLocation, TalentMapPage } from "../api/types";

/**
 * The mapping panel's shape: country → company → executives, with the two groups that fall outside
 * it — executives mapped at no company of the mandate, and rows with no place at all.
 *
 * <p>An executive with no location of their own inherits their company's point (`seatedAt`), so a
 * company with three people reads as one pin with three around it. One with a place of their own is
 * drawn there, wherever their employer sits.
 */

export interface TreeExecutive {
  kind: "executive";
  id: string;
  candidate: Candidate;
  location: MapLocation | null;
  /** The company whose point this person is drawn at when they have none of their own. */
  seatedAt: string | null;
}

export interface TreeCompany {
  kind: "company";
  id: string;
  company: TriageCompany;
  location: MapLocation | null;
  executives: TreeExecutive[];
}

export interface TreeCountry {
  key: string;
  name: string;
  companies: TreeCompany[];
  /** Executives located in this country whose employer is not in the mandate. */
  unmapped: TreeExecutive[];
  companyCount: number;
  executiveCount: number;
}

export interface TreeCounts {
  countries: number;
  companies: number;
  executives: number;
  /** Rows the globe draws no pin for: no place of their own, and no company's to borrow. */
  unlocated: number;
}

/**
 * Named apart from the component that renders it (`components/TalentMapTree.tsx`): a data shape and
 * the React component drawing it are two different things, and one name for both made that file
 * import this one under an alias.
 */
export interface MappingTree {
  countries: TreeCountry[];
  /** Companies and executives with nowhere to be drawn — no city, no country, or a place nobody could find. */
  unlocated: { companies: TreeCompany[]; executives: TreeExecutive[] };
  counts: TreeCounts;
}

/** Where a located row without a country is filed — a city the export named with no country beside it. */
export const OTHER_COUNTRY = "Other";

export function buildTree(page: TalentMapPage): MappingTree {
  const byCompany = new Map<string, TreeExecutive[]>();
  const unmapped: TreeExecutive[] = [];
  for (const candidate of page.candidates) {
    const location = page.locations[candidate.id] ?? null;
    const node: TreeExecutive = {
      kind: "executive",
      id: candidate.id,
      candidate,
      location,
      seatedAt: null,
    };
    if (candidate.triageCompanyId) {
      const held = byCompany.get(candidate.triageCompanyId);
      if (held) held.push(node);
      else byCompany.set(candidate.triageCompanyId, [node]);
    } else {
      unmapped.push(node);
    }
  }

  // Every country the server put a code to, by the name it gave it — so a row that has no resolved
  // place still joins the group its coded neighbours made rather than opening a second one.
  const codeByName = new Map<string, string>();
  for (const location of Object.values(page.locations)) {
    if (location.country && location.countryCode) {
      codeByName.set(location.country.toLowerCase(), location.countryCode);
    }
  }

  const countries = new Map<string, TreeCountry>();
  /**
   * The country a row is filed under: where it was actually drawn, not where its snapshot says it
   * is — a company follows its people, so grouping on the snapshot filed one under a country its own
   * pin had left. Keyed on the ISO code where there is one, so "UAE" and "United Arab Emirates" are
   * one group, titled with the catalog's English name.
   */
  const countryOf = (location: MapLocation | null, snapshot: string | null): TreeCountry | null => {
    const display = location?.country ?? snapshot?.trim() ?? null;
    const name = display || (location ? OTHER_COUNTRY : null);
    if (!name) return null;
    const code = location?.countryCode ?? codeByName.get(name.toLowerCase()) ?? null;
    const key = (code ?? name).toLowerCase();
    let country = countries.get(key);
    if (!country) {
      country = { key, name, companies: [], unmapped: [], companyCount: 0, executiveCount: 0 };
      countries.set(key, country);
    }
    // A coded name outranks whichever spelling arrived first.
    if (location?.country) country.name = location.country;
    return country;
  };

  const unlocatedCompanies: TreeCompany[] = [];
  const unlocatedExecutives: TreeExecutive[] = [];
  const companies = [...page.companies].sort(byName);
  for (const company of companies) {
    const location = page.locations[company.id] ?? null;
    const executives = (byCompany.get(company.id) ?? []).sort(bySeniorityThenName);
    for (const executive of executives) {
      if (!executive.location && location) executive.seatedAt = company.id;
    }
    const node: TreeCompany = { kind: "company", id: company.id, company, location, executives };
    const country = countryOf(location, company.companyCountry);
    if (!country) {
      unlocatedCompanies.push(node);
      continue;
    }
    country.companies.push(node);
  }

  for (const executive of unmapped.sort(bySeniorityThenName)) {
    const country = countryOf(executive.location, executive.candidate.locationCountry);
    if (!country) {
      unlocatedExecutives.push(executive);
      continue;
    }
    country.unmapped.push(executive);
  }

  for (const country of countries.values()) {
    country.companyCount = country.companies.length;
    country.executiveCount =
      country.companies.reduce((sum, company) => sum + company.executives.length, 0) +
      country.unmapped.length;
  }

  const ordered = [...countries.values()].sort(
    (a, b) => b.companyCount - a.companyCount || a.name.localeCompare(b.name),
  );

  const unlocated = { companies: unlocatedCompanies, executives: unlocatedExecutives };
  return { countries: ordered, unlocated, counts: countsOf(ordered, unlocated) };
}

/**
 * What the panel's header says, counted off the tree rather than off the page — so a filtered tree
 * counts what it holds rather than what it was narrowed from.
 *
 * <p>A row is located when the globe draws a pin for it, which is the one rule
 * `toFeatureCollection` applies: a point of its own, or — for a person — their company's to sit
 * beside. Nothing else counts, wherever the tree happens to file the row: a country name with no
 * point behind it puts an executive under that country and still draws nothing.
 */
function countsOf(
  countries: TreeCountry[],
  unlocated: { companies: TreeCompany[]; executives: TreeExecutive[] },
): TreeCounts {
  let companies = 0;
  let executives = 0;
  let unplaced = 0;
  const countExecutive = (executive: TreeExecutive) => {
    executives++;
    if (!executive.location && !executive.seatedAt) unplaced++;
  };
  const countCompany = (company: TreeCompany) => {
    companies++;
    if (!company.location) unplaced++;
    company.executives.forEach(countExecutive);
  };

  for (const country of countries) {
    country.companies.forEach(countCompany);
    country.unmapped.forEach(countExecutive);
  }
  unlocated.companies.forEach(countCompany);
  unlocated.executives.forEach(countExecutive);

  return {
    countries: countries.filter((country) => country.name !== OTHER_COUNTRY).length,
    companies,
    executives,
    unlocated: unplaced,
  };
}

/**
 * The tree narrowed to what matches, by company name, person, title or city. A company stays with
 * all its people when it matches itself, and with only the matching people when it does not — the
 * reader typed a name and wants to see where that name sits.
 */
export function filterTree(tree: MappingTree, query: string): MappingTree {
  const needle = query.trim().toLowerCase();
  if (!needle) return tree;

  const matchesExecutive = (executive: TreeExecutive) =>
    [executive.candidate.fullName, executive.candidate.title, executive.candidate.locationCity]
      .some((field) => field?.toLowerCase().includes(needle));
  const matchesCompany = (company: TreeCompany) =>
    [company.company.companyName, company.company.companyCity, company.company.industry]
      .some((field) => field?.toLowerCase().includes(needle));

  const narrowCompany = (company: TreeCompany): TreeCompany | null => {
    if (matchesCompany(company)) return company;
    const executives = company.executives.filter(matchesExecutive);
    return executives.length ? { ...company, executives } : null;
  };

  const countries: TreeCountry[] = [];
  for (const country of tree.countries) {
    const companies = country.companies.map(narrowCompany).filter((c): c is TreeCompany => c !== null);
    const unmapped = country.unmapped.filter(matchesExecutive);
    if (!companies.length && !unmapped.length) continue;
    countries.push({
      ...country,
      companies,
      unmapped,
      companyCount: companies.length,
      executiveCount:
        companies.reduce((sum, company) => sum + company.executives.length, 0) + unmapped.length,
    });
  }
  const unlocated = {
    companies: tree.unlocated.companies.map(narrowCompany).filter((c): c is TreeCompany => c !== null),
    executives: tree.unlocated.executives.filter(matchesExecutive),
  };

  return { countries, unlocated, counts: countsOf(countries, unlocated) };
}

/** Every company and executive node the tree holds, in panel order. */
export function nodesOf(tree: MappingTree): (TreeCompany | TreeExecutive)[] {
  const nodes: (TreeCompany | TreeExecutive)[] = [];
  for (const country of tree.countries) {
    for (const company of country.companies) {
      nodes.push(company, ...company.executives);
    }
    nodes.push(...country.unmapped);
  }
  for (const company of tree.unlocated.companies) nodes.push(company, ...company.executives);
  nodes.push(...tree.unlocated.executives);
  return nodes;
}

/**
 * The country and company a row sits under, so selecting a pin can open the branches above its row.
 * Both null for a row that is not in the tree.
 */
export function pathTo(tree: MappingTree, id: string): { country: string | null; company: string | null } {
  for (const country of tree.countries) {
    for (const company of country.companies) {
      if (company.id === id) return { country: country.key, company: null };
      if (company.executives.some((executive) => executive.id === id)) {
        return { country: country.key, company: company.id };
      }
    }
    if (country.unmapped.some((executive) => executive.id === id)) {
      return { country: country.key, company: null };
    }
  }
  for (const company of tree.unlocated.companies) {
    if (company.id === id) return { country: UNLOCATED_KEY, company: null };
    if (company.executives.some((executive) => executive.id === id)) {
      return { country: UNLOCATED_KEY, company: company.id };
    }
  }
  if (tree.unlocated.executives.some((executive) => executive.id === id)) {
    return { country: UNLOCATED_KEY, company: null };
  }
  return { country: null, company: null };
}

/** The "No location" group's key in the panel's expand state, beside the countries' own. */
export const UNLOCATED_KEY = "__unlocated";

function byName(a: TriageCompany, b: TriageCompany): number {
  return a.companyName.localeCompare(b.companyName, undefined, { sensitivity: "base" });
}

function bySeniorityThenName(a: TreeExecutive, b: TreeExecutive): number {
  const rank = (executive: TreeExecutive) => {
    const index = executive.candidate.seniority
      ? SENIORITY_TOKENS.indexOf(executive.candidate.seniority)
      : -1;
    return index === -1 ? SENIORITY_TOKENS.length : index;
  };
  return (
    rank(a) - rank(b) ||
    a.candidate.fullName.localeCompare(b.candidate.fullName, undefined, { sensitivity: "base" })
  );
}
