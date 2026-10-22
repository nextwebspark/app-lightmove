import { describe, expect, it } from "vitest";
import { createTriageCompanyColumns } from "./triageCompanyColumns";

const idsOf = (staff: boolean) => createTriageCompanyColumns([], { staff }).map((column) => column.id);

describe("createTriageCompanyColumns", () => {
  it("draws who added a company for staff", () => {
    expect(idsOf(true)).toContain("addedBy");
  });

  it("leaves a client seat neither the notes nor who added a company", () => {
    expect(idsOf(false)).not.toContain("addedBy");
    expect(idsOf(false)).not.toContain("note");
  });
});
