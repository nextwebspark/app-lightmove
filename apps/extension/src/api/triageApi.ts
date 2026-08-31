import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { TriageCompanyMatch } from "./types";

interface TriageCompaniesPage {
  companies: TriageCompanyMatch[];
  totalCount: number;
}

const PAGE_SIZE = 25;

/**
 * How far to page before giving up. `q` is a substring match, so a common word can return hundreds and
 * a popup must not spend that many requests; past this the person is filed unmapped, which the screen
 * already says and the Companies grid can fix.
 */
const MAX_PAGES = 5;

/**
 * The company a mandate already holds under exactly this name, so a captured person can be mapped to it.
 *
 * Both stages are asked, shortlisted first, because `GET /triage` reads one at a time and defaults to
 * `inUniverse` — which is not where mapped people are. `q` is a substring match, so the name is
 * compared exactly here and the pages are walked rather than trusting the first.
 */
export async function findTriageCompanyByName(
  api: LightMoveApiClient,
  projectId: string,
  name: string,
): Promise<TriageCompanyMatch | null> {
  const wanted = name.trim();
  for (const status of ["shortlisted", "inUniverse"] as const) {
    for (let page = 0; page < MAX_PAGES; page += 1) {
      const held = await api.request<TriageCompaniesPage>(
        `/projects/${projectId}/triage?status=${status}&size=${PAGE_SIZE}&page=${page}`
          + `&q=${encodeURIComponent(wanted)}`,
      );
      const matched = held.companies.find((company) => isSameName(company.companyName, wanted));
      if (matched) {
        return matched;
      }
      if (held.companies.length < PAGE_SIZE || (page + 1) * PAGE_SIZE >= held.totalCount) {
        break;
      }
    }
  }
  return null;
}

function isSameName(held: string, wanted: string): boolean {
  return held.trim().localeCompare(wanted, undefined, { sensitivity: "accent" }) === 0;
}
