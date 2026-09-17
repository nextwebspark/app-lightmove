import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { TEMPLATE_COLUMNS, TEMPLATE_SORT_FIELDS } from "./templateColumns";

describe("TEMPLATE_SORT_FIELDS", () => {
  it.each(["library", "workspace"] as const)(
    "names exactly the %s columns that sort, so a remembered sort is never thrown away",
    (scope) => {
      expect([...TEMPLATE_SORT_FIELDS[scope]].sort()).toEqual(sortableFieldsOf(TEMPLATE_COLUMNS[scope]).sort());
    },
  );
});
