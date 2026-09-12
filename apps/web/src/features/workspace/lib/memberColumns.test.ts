import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { memberColumns, MEMBER_SORT_FIELDS } from "./memberColumns";

describe("MEMBER_SORT_FIELDS", () => {
  it("names exactly the columns that sort, so a remembered sort is never thrown away", () => {
    expect([...MEMBER_SORT_FIELDS].sort()).toEqual(sortableFieldsOf(memberColumns).sort());
  });
});
