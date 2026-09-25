import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { PROJECT_COLUMN_PINNING, projectColumns, PROJECT_SORT_FIELDS } from "./projectColumns";

describe("PROJECT_SORT_FIELDS", () => {
  it("names exactly the columns that sort, so a remembered sort is never thrown away", () => {
    expect([...PROJECT_SORT_FIELDS].sort()).toEqual(sortableFieldsOf(projectColumns).sort());
  });
});

describe("projectColumns", () => {
  it("carries no business-unit column — the group header names it instead", () => {
    expect(projectColumns.map((column) => column.id)).not.toContain("client");
  });

  it("pins the position, the column a scrolled row must not lose", () => {
    expect(PROJECT_COLUMN_PINNING.start).toEqual(["position"]);
  });
});
