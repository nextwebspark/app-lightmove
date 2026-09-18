import { requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type { TriageCompanyStatus } from "./types";

/**
 * The Companies grid as a file.
 *
 * The server composes it — one stage whole, not the page on screen — so this only says which stage
 * and what the search box narrowed it to. `fileNameParts` comes from the caller because this is the
 * one thing the server cannot name well: the mandate's own client and position title live there.
 */
export async function saveCompaniesCsv(
  projectId: string,
  status: TriageCompanyStatus,
  query: string,
  fileNameParts: readonly string[],
): Promise<void> {
  const params = new URLSearchParams({ status });
  if (query.trim()) params.set("q", query.trim());

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
