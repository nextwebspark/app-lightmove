import type { PoolFilters } from "../api/types";

/** The Candidates page with nothing narrowed: everyone, newest activity first. */
export const NO_POOL_FILTERS: PoolFilters = {
  q: "",
  view: "all",
  tagIds: [],
  tagMatch: "any",
  position: "",
  status: "",
  owner: "",
  country: "",
  sort: "activity",
  direction: "desc",
};

/** How many filters beyond the search and the quick view are in force — the Filters button's badge. */
export function countActiveFilters(filters: PoolFilters): number {
  return [filters.tagIds.length > 0, filters.position, filters.status, filters.owner, filters.country].filter(Boolean)
    .length;
}
