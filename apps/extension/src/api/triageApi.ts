import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { TriageCompanyMatch } from "./types";

interface TriageCompaniesPage {
  companies: TriageCompanyMatch[];
}

/**
 * The company a mandate already holds under exactly this name, so a captured person can be mapped to it.
 *
 * Two requests, because `GET /triage` reads one stage at a time and defaults to `inUniverse` — a single
 * call would miss every shortlisted company, which is exactly where people get mapped. Shortlisted is
 * asked first so it wins a name held at both stages; `declined` is deliberately never searched.
 *
 * `q` is a substring match server-side — "Emirates NBD" would find "Emirates NBD Capital" — so the
 * name is compared exactly here rather than trusting what came back.
 */
export async function findTriageCompanyByName(
  api: LightMoveApiClient,
  projectId: string,
  name: string,
): Promise<TriageCompanyMatch | null> {
  const wanted = name.trim();
  for (const status of ["shortlisted", "inUniverse"] as const) {
    const page = await api.request<TriageCompaniesPage>(
      `/projects/${projectId}/triage?status=${status}&size=25&q=${encodeURIComponent(wanted)}`,
    );
    const matched = page.companies.find((company) => isSameName(company.companyName, wanted));
    if (matched) {
      return matched;
    }
  }
  return null;
}

function isSameName(held: string, wanted: string): boolean {
  return held.trim().localeCompare(wanted, undefined, { sensitivity: "accent" }) === 0;
}
