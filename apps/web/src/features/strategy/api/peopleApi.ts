import { request } from "../../../lib/apiClient";
import type { TriageCompanyStatus } from "../../triage/api/types";
import type {
  AddPeopleResult,
  PeopleCount,
  PeopleFacets,
  PeopleFilter,
  PeopleSearchPage,
  PeopleSearchResults,
  PlaceSuggestion,
  Strategy,
} from "./types";

/**
 * Strategy's People mode over ContactOut. Every read and search acts on the mandate's *stored* people
 * filter, never a payload, so a caller flushes its autosave before searching.
 */

export const PEOPLE_FACETS_KEY = ["peopleFacets"] as const;

export const PEOPLE_COUNT_KEY = (projectId: string) => ["peopleCount", projectId] as const;

export const PEOPLE_SEARCH_KEY_PREFIX = (projectId: string) => ["peopleSearch", projectId] as const;

/**
 * `run` is bumped by each Search press, so a new press is a new result set rather than a refetch. Run 0
 * is what was already bought for the stored filter, read back when the screen opens.
 */
export const PEOPLE_SEARCH_KEY = (projectId: string, run: number) =>
  [...PEOPLE_SEARCH_KEY_PREFIX(projectId), run] as const;

export const PEOPLE_RESULTS_KEY = (projectId: string) => ["peopleResults", projectId] as const;

export const LOCATION_SUGGESTIONS_KEY = (query: string) => ["locationSuggestions", query] as const;

export function putPeopleFilter(projectId: string, filter: PeopleFilter): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/people/filter`, {
    method: "PUT",
    body: { filter },
  });
}

export function getPeopleFacets(): Promise<PeopleFacets> {
  return request<PeopleFacets>("/companies/people-facets");
}

export function getPeopleCount(projectId: string, signal?: AbortSignal): Promise<PeopleCount> {
  return request<PeopleCount>(`/projects/${projectId}/strategy/people/count`, { signal });
}

/** A POST: a page not already cached is bought, one search credit per person on it. */
export function searchPeople(projectId: string, page: number): Promise<PeopleSearchPage> {
  return request<PeopleSearchPage>(`/projects/${projectId}/strategy/people/search?page=${page}`, {
    method: "POST",
  });
}

/** The stored filter's pages already bought, in order — free, and never buys one. */
export function getPeopleResults(projectId: string): Promise<PeopleSearchResults> {
  return request<PeopleSearchResults>(`/projects/${projectId}/strategy/people/results`);
}

/** `status` is the stage each person's employer is filed at; one the mandate holds stays where it is. */
export function addPeople(
  projectId: string,
  linkedinSlugs: string[],
  status: TriageCompanyStatus,
): Promise<AddPeopleResult> {
  return request<AddPeopleResult>(`/projects/${projectId}/strategy/people/add`, {
    method: "POST",
    body: { linkedinSlugs, status },
  });
}

export async function suggestLocations(query: string, signal?: AbortSignal): Promise<PlaceSuggestion[]> {
  const answer = await request<{ places: PlaceSuggestion[] }>(
    `/locations/suggest?q=${encodeURIComponent(query)}`,
    { signal },
  );
  return answer.places;
}
