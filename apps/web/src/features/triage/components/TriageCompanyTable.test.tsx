import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { Candidate } from "../../candidates/api/types";
import { EMPTY_GRID_LAYOUT } from "../../../lib/useGridLayout";
import { TriageCompanyTable } from "./TriageCompanyTable";

const layla: Candidate = {
  id: "c1",
  triageCompanyId: "u1",
  companyName: "ACWA Power",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "engaged",
  linkedinUrl: null,
  locationCountry: null,
  locationCity: null,
  nationality: null,
  gender: null,
  yearsExperience: null,
  aiInferredFields: [],
  summary: null,
  personId: "person-1",
  linkedinUrlLocked: false,
  compensation: {
    currency: null, baseSalary: null, bonus: null, allowances: null,
    longTermIncentive: null, noticePeriod: null, allowanceLines: [], longTermIncentiveTypes: [],
    expectedBaseSalary: null, expectedTotalPackage: null, expectedPackageNote: null, availableFrom: null,
    mobility: null, mobilityNote: null,
  },
  career: [],
  education: [],
  skills: [],
  languages: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
  contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
};

function renderCards(busyIds: ReadonlySet<string>) {
  const noop = vi.fn();
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TriageCompanyTable
          rows={[{ company: null, candidate: layla }]}
          label="In universe"
          sort={{ field: "companyName", direction: "asc" } as never}
          onSortChange={noop}
          columnVisibility={{}}
          onColumnVisibilityChange={noop}
          customColumns={[]}
          layout={EMPTY_GRID_LAYOUT}
          onLayoutChange={noop}
          loading={false}
          error={false}
          emptyMessage="Nobody here"
          projectId="p1"
          onMove={noop}
          onDelete={noop}
          onAddExecutive={noop}
          onOpenCompanyNotes={noop}
          onChangeCandidateStatus={noop}
          onEditCandidate={noop}
          onRemoveCandidate={noop}
          onMoveCandidate={noop}
          onOpenCompany={noop}
          busyIds={busyIds}
          canWrite
        />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("TriageCompanyTable on a phone", () => {
  const desktop = window.matchMedia;
  beforeEach(() => {
    window.matchMedia = (query: string) => ({ ...desktop(query), matches: false });
  });
  afterEach(() => {
    window.matchMedia = desktop;
  });

  it("holds a person's status picker while their change saves, so a second pick cannot race it", () => {
    renderCards(new Set(["c1"]));
    expect(screen.getByRole("button", { name: /^Status of Yasmin El-Sayed/ })).toBeDisabled();
  });

  it("offers the picker again once nothing of theirs is saving", () => {
    renderCards(new Set());
    expect(screen.getByRole("button", { name: /^Status of Yasmin El-Sayed/ })).toBeEnabled();
  });
});
