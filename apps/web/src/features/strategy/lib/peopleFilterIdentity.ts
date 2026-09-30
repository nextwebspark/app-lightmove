import type { PeopleFilter } from "../api/types";

/**
 * Whether two people filters ask the same question — what marks a saved people search as the one on
 * screen. Lists are sets, as the server's cache key treats them, and an absent match mode is the
 * default one.
 */
export function samePeopleFilter(a: PeopleFilter, b: PeopleFilter): boolean {
  const lists: (keyof PeopleFilter)[] = [
    "jobTitles",
    "excludedJobTitles",
    "seniorities",
    "jobFunctions",
    "skills",
    "yearsInCurrentRole",
    "yearsOfExperience",
    "locations",
    "companies",
    "domains",
    "excludedCompanies",
    "companySizes",
    "industries",
    "excludedIndustries",
    "education",
    "contactTypes",
  ];
  return (
    lists.every((key) => sameTokens(a[key] as string[], b[key] as string[])) &&
    sameTokens(
      a.languages.map((language) => language.language),
      b.languages.map((language) => language.language),
    ) &&
    (a.name ?? null) === (b.name ?? null) &&
    (a.keyword ?? null) === (b.keyword ?? null) &&
    (a.titleMatch ?? "current") === (b.titleMatch ?? "current") &&
    (a.companyMatch ?? "current") === (b.companyMatch ?? "current") &&
    a.includeRelatedTitles === b.includeRelatedTitles &&
    a.recentlyChangedJobs === b.recentlyChangedJobs &&
    (a.locationRadius ?? null) === (b.locationRadius ?? null)
  );
}

function sameTokens(a: string[] | null | undefined, b: string[] | null | undefined): boolean {
  const left = [...(a ?? [])].sort();
  const right = [...(b ?? [])].sort();
  return left.length === right.length && left.every((value, index) => value === right[index]);
}
