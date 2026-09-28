import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { vocabularyFor } from "../../workspace/lib/vocabulary";
import { clientColumnsFor, CLIENT_SORT_FIELDS } from "./clientColumns";

describe("CLIENT_SORT_FIELDS", () => {
  it("names exactly the columns that sort, so a remembered sort is never thrown away", () => {
    const columns = clientColumnsFor(vocabularyFor("COMPANY"));
    expect([...CLIENT_SORT_FIELDS].sort()).toEqual(sortableFieldsOf(columns).sort());
  });
});

describe("clientColumnsFor", () => {
  it("heads the columns with what the workspace calls its clients and their people", () => {
    const headers = (mode: "AGENCY" | "COMPANY") =>
      clientColumnsFor(vocabularyFor(mode)).map((column) => column.header);
    expect(headers("COMPANY")).toEqual(["Business unit", "Hiring managers", "Open positions", "Viewers"]);
    expect(headers("AGENCY")).toEqual(["Client", "Client contacts", "Open positions", "Viewers"]);
  });
});
