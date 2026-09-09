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
  /** Rows with a point of their own or their company's. */
  located: number;
  unlocated: number;
}

export interface TalentMapTree {
  countries: TreeCountry[];
  /** Companies and executives with nowhere to be drawn — no city, no country, or a place nobody could find. */
  unlocated: { companies: TreeCompany[]; executives: TreeExecutive[] };
  counts: TreeCounts;
}

/** Where a located row without a country is filed — a city the export named with no country beside it. */
export const OTHER_COUNTRY = "Other";

export function buildTree(page: TalentMapPage): TalentMapTree {
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

  const countries = new Map<string, TreeCountry>();
  const countryOf = (name: string | null, located: boolean): TreeCountry | null => {
    const trimmed = name?.trim();
    const display = trimmed || (located ? OTHER_COUNTRY : null);
    if (!display) return null;
    const key = display.toLowerCase();
    let country = countries.get(key);
    if (!country) {
      country = { key, name: display, companies: [], unmapped: [], companyCount: 0, executiveCount: 0 };
      countries.set(key, country);
    }
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
    const country = countryOf(company.companyCountry, location !== null);
    if (!country) {
      unlocatedCompanies.push(node);
      continue;
    }
    country.companies.push(node);
  }

  for (const executive of unmapped.sort(bySeniorityThenName)) {
    const country = countryOf(executive.candidate.locationCountry, executive.location !== null);
    if (!country) {
      unlocatedExecutives.push(executive);
      continue;
    }
    country.unmapped.push(executive);
  }

  // A company's people are drawn where the company is, so an executive counts as located when their
  // company does — and stays unlocated, though listed under the company, when neither has a place.
  let located = 0;
  let unlocated = unlocatedCompanies.length + unlocatedExecutives.length;
  for (const country of countries.values()) {
    country.companyCount = country.companies.length;
    country.executiveCount =
      country.companies.reduce((sum, company) => sum + company.executives.length, 0) +
      country.unmapped.length;
    for (const company of country.companies) {
      if (company.location) located++;
      else unlocated++;
      for (const executive of company.executives) {
        if (executive.location || executive.seatedAt) located++;
        else unlocated++;
      }
    }
    located += country.unmapped.length;
  }
  for (const company of unlocatedCompanies) unlocated += company.executives.length;

  const ordered = [...countries.values()].sort(
    (a, b) => b.companyCount - a.companyCount || a.name.localeCompare(b.name),
  );

  return {
    countries: ordered,
    unlocated: { companies: unlocatedCompanies, executives: unlocatedExecutives },
    counts: {
      countries: ordered.filter((country) => country.name !== OTHER_COUNTRY).length,
      companies: page.companies.length,
      executives: page.candidates.length,
      located,
      unlocated,
    },
  };
}

/**
 * The tree narrowed to what matches, by company name, person, title or city. A company stays with
 * all its people when it matches itself, and with only the matching people when it does not — the
 * reader typed a name and wants to see where that name sits.
 */
export function filterTree(tree: TalentMapTree, query: string): TalentMapTree {
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

  const companyCount =
    countries.reduce((sum, country) => sum + country.companyCount, 0) + unlocated.companies.length;
  const executiveCount =
    countries.reduce((sum, country) => sum + country.executiveCount, 0) +
    unlocated.companies.reduce((sum, company) => sum + company.executives.length, 0) +
    unlocated.executives.length;

  return {
    countries,
    unlocated,
    counts: {
      ...tree.counts,
      countries: countries.filter((country) => country.name !== OTHER_COUNTRY).length,
      companies: companyCount,
      executives: executiveCount,
    },
  };
}

/** Every company and executive node the tree holds, in panel order. */
export function nodesOf(tree: TalentMapTree): (TreeCompany | TreeExecutive)[] {
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
export function pathTo(tree: TalentMapTree, id: string): { country: string | null; company: string | null } {
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
