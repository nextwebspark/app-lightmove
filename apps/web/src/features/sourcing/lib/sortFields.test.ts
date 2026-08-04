import { describe, expect, it } from "vitest";
import type { SourcingSortField } from "../api/types";
import { sourcingColumns } from "../components/columns";

/**
 * Every sortable column's id is sent to the API verbatim as its `sort` token, and the server rejects
 * an unknown one with a 400 that the table renders as an empty list. A typo in a column id is
 * therefore invisible until someone clicks that header, so the two lists are pinned against each
 * other here — the same mirror convention the company-size bands use.
 */
const BACKEND_SORT_TOKENS: SourcingSortField[] = [
  "name",
  "tier",
  "sector",
  "employees",
  "revenue",
  "location",
  "founded",
  "country",
];

describe("sortable column ids mirror the backend's CompanySortField tokens", () => {
  const sortableIds = sourcingColumns
    .filter((column) => column.enableSorting !== false)
    .map((column) => column.id as string);

  it("sends only ids the server's allowlist resolves", () => {
    expect([...sortableIds].sort()).toEqual([...BACKEND_SORT_TOKENS].sort());
  });

  it("marks every other column unsortable rather than sending an id the server rejects", () => {
    const unsortable = sourcingColumns
      .filter((column) => column.enableSorting === false)
      .map((column) => column.id as string);

    for (const id of unsortable) {
      expect(BACKEND_SORT_TOKENS).not.toContain(id as SourcingSortField);
    }
  });
});
