import { requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type { TriageCompanyStatus } from "./types";

/** The grid's three header filters, exactly as its own read takes them. */
export interface CompaniesExportFilters {
  query: string;
  executiveQuery: string;
  executiveStatuses: readonly string[];
}

/**
 * The Companies grid as a file.
 *
 * The server composes it — one stage whole, not the page on screen — so this only says which stage
 * and what the grid's headers narrowed it to. `fileNameParts` comes from the caller because this is
 * the one thing the server cannot name well: the mandate's own client and position title live there.
 */
export async function saveCompaniesCsv(
  projectId: string,
  status: TriageCompanyStatus,
  filters: CompaniesExportFilters,
  fileNameParts: readonly string[],
): Promise<void> {
  const params = new URLSearchParams({ status });
  // Omitted rather than sent empty, as the grid's own read does: the server reads a blank filter as
  // no filter, and leaving it out keeps the two states from being one request apart in the log.
  if (filters.query.trim()) params.set("q", filters.query.trim());
  if (filters.executiveQuery.trim()) params.set("executiveQuery", filters.executiveQuery.trim());
  // Repeated rather than joined, as the grid's read does: the server binds it as a list.
  filters.executiveStatuses.forEach((status) => params.append("executiveStatuses", status));

  const blob = await requestBlob(`/projects/${projectId}/export/companies?${params}`);
  saveBlob(blob, fileNameOf(["uncava", ...fileNameParts, today()]));
}

/** A name a browser and a file system will both take: anything but a letter or digit becomes a dash. */
function fileNameOf(parts: readonly string[]): string {
  const slug = parts
    .map((part) => part.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""))
    .filter((part) => part.length > 0)
    .join("-");
  return `${slug || "uncava"}.csv`;
}

/** Dated, because a mandate's universe is a moving thing and two exports are two snapshots. */
function today(): string {
  return new Date().toISOString().slice(0, 10);
}
