import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useDebouncedValue } from "../../../lib/useComboboxList";
import * as sourcingApi from "../api/sourcingApi";
import type { SearchCriteria } from "../api/sourcingApi";

const COUNT_AFTER_MS = 500;

/**
 * What the search on screen would reach, counted free half a second after the last edit. The last answer stays
 * meanwhile while the companies are the same; another set of companies shows nothing until it is counted.
 */
export function useSourcingCounts(projectId: string, companyIds: readonly string[], criteria: SearchCriteria | null) {
  const settled = useDebouncedValue(criteria, COUNT_AFTER_MS);
  const searchable = settled !== null && (settled.functionTerms.length > 0 || settled.standaloneTitles.length > 0);
  const counts = useQuery({
    queryKey: settled ? sourcingApi.sourcingKeys.counts(projectId, companyIds, settled) : ["executiveSourcing", "idle"],
    queryFn: ({ signal }) => sourcingApi.countSearch(projectId, companyIds, settled!, signal),
    enabled: searchable,
    placeholderData: (previous, previousQuery) =>
      sameCompanies(previousQuery?.queryKey[3], companyIds) ? keepPreviousData(previous) : undefined,
    staleTime: 60_000,
  });
  return {
    counts: searchable ? (counts.data ?? null) : null,
    counting: searchable && (counts.isFetching || settled !== criteria),
  };
}

function sameCompanies(previous: unknown, companyIds: readonly string[]): boolean {
  return (
    Array.isArray(previous) &&
    previous.length === companyIds.length &&
    previous.every((id, index) => id === companyIds[index])
  );
}
