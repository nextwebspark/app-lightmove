import { useEffect, useState } from "react";
import type { CompanySort, CompanySortField, SortDirection } from "../api/types";

const STORAGE_PREFIX = "lm.strategy.sort.";

const FIELDS: CompanySortField[] = [
  "name",
  "sector",
  "country",
  "location",
  "employees",
  "revenue",
  "founded",
];

/**
 * Which column this mandate is sorted by, remembered per project in `localStorage`, beside the
 * column layout it belongs with. The filter is the server's and survives a navigation; a sort that
 * resets while the filter holds makes the same screen come back half-remembered.
 */
export function useCompanySort(projectId: string, initial: CompanySort) {
  const [sort, setSort] = useState<CompanySort>(() => read(projectId, initial));

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_PREFIX + projectId, JSON.stringify(sort));
    } catch {
      // A blocked store costs a sort order, not the table.
    }
  }, [projectId, sort]);

  return [sort, setSort] as const;
}

function read(projectId: string, fallback: CompanySort): CompanySort {
  try {
    const stored = localStorage.getItem(STORAGE_PREFIX + projectId);
    if (!stored) return fallback;
    const parsed: unknown = JSON.parse(stored);
    if (typeof parsed !== "object" || parsed === null) return fallback;
    const { field, direction } = parsed as Partial<CompanySort>;
    // A field dropped from the allowlist since the write would be a 400 on every page load.
    if (!FIELDS.includes(field as CompanySortField)) return fallback;
    if (direction !== "asc" && direction !== "desc") return fallback;
    return { field: field as CompanySortField, direction: direction as SortDirection };
  } catch {
    return fallback;
  }
}
