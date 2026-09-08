import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { projectColumns, PROJECT_SORT_FIELDS } from "./projectColumns";

describe("PROJECT_SORT_FIELDS", () => {
  it("names exactly the columns that sort, so a remembered sort is never thrown away", () => {
    expect([...PROJECT_SORT_FIELDS].sort()).toEqual(sortableFieldsOf(projectColumns).sort());
  });
});
