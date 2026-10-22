import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it } from "vitest";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../api/types";
import { createTriageCompanyColumns } from "./triageCompanyColumns";
import type { TriageCompanyRow } from "./triageRows";

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

const YARA = { userId: "u-yara", name: "Yara Haddad", avatarUrl: null };
const OMAR = { userId: "u-omar", name: "Omar Saleh", avatarUrl: null };
const COMPANY = {
  id: "t1",
  companyName: "ACWA Power",
  addedAt: "2026-08-01T09:00:00Z",
  addedByUserId: YARA.userId,
  addedByName: YARA.name,
  addedByAvatarUrl: null,
} as TriageCompany;
const EXECUTIVE = { id: "c1", fullName: "Layla Haddad" } as Candidate;

function renderAddedBy(row: TriageCompanyRow) {
  const column = createTriageCompanyColumns([], { staff: true }).find((candidate) => candidate.id === "addedBy");
  const cell = column?.cell as (info: { row: { original: TriageCompanyRow } }) => ReactElement;
  return render(cell({ row: { original: row } }));
}

describe("the Added by cell", () => {
  it("shows the company's adder alone when the same colleague filed the executive", () => {
    renderAddedBy({ company: COMPANY, candidate: EXECUTIVE, candidateAddedBy: YARA });

    expect(screen.getByText("Yara H.")).toBeInTheDocument();
    expect(screen.queryByText("OS")).not.toBeInTheDocument();
  });

  it("adds the executive's adder beside the company's when they differ, and names both on hover", () => {
    renderAddedBy({ company: COMPANY, candidate: EXECUTIVE, candidateAddedBy: OMAR });

    expect(screen.getByText("Yara H.")).toBeInTheDocument();
    expect(screen.getByText("OS")).toBeInTheDocument();
    expect(screen.getByText(/Yara H\./).closest("[title]")).toHaveAttribute(
      "title",
      "Company: Yara Haddad\nExecutive: Omar Saleh",
    );
  });

  it("shows the executive's adder on a line with no company in the mandate", () => {
    renderAddedBy({ company: null, candidate: EXECUTIVE, candidateAddedBy: OMAR });

    expect(screen.getByText("Omar S.")).toBeInTheDocument();
  });
});
