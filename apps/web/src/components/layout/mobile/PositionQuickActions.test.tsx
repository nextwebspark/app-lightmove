import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as pipelineApi from "../../../features/pipeline/api/pipelineApi";
import type { PipelineRow } from "../../../features/pipeline/api/types";
import { PositionQuickActions } from "./PositionQuickActions";

vi.mock("../../../features/pipeline/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../features/pipeline/api/pipelineApi")>()),
  getPipeline: vi.fn(),
}));

const rowOf = (index: number): PipelineRow => ({
  candidateId: `c${index}`,
  personId: `p${index}`,
  fullName: `Executive ${index}`,
  title: "Chief Financial Officer",
  companyName: "ACWA Power",
  triageCompanyId: null,
  status: "identified",
  statusLabel: "Identified",
  statusColour: "grey",
  pipelineStage: { key: "identified", label: "Identified", colour: "grey", kind: "active", position: 1 },
  addedAt: "2026-10-01T00:00:00Z",
  statusChangedAt: "2026-10-01T00:00:00Z",
  nextAction: null,
  clientVisible: false,
});

function Landed() {
  const { pathname, search } = useLocation();
  return <span data-testid="landed">{`${pathname}${search}`}</span>;
}

function renderActions() {
  render(
    <MemoryRouter initialEntries={["/projects/p1/companies/universe"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <PositionQuickActions projectId="p1" open onClose={() => {}} />
        <Routes>
          <Route path="*" element={<Landed />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("PositionQuickActions", () => {
  beforeEach(() => {
    vi.mocked(pipelineApi.getPipeline).mockResolvedValue({
      rows: Array.from({ length: 34 }, (_, index) => rowOf(index + 1)),
      stageCounts: [],
    } as unknown as Awaited<ReturnType<typeof pipelineApi.getPipeline>>);
  });

  it("sends Add company to the Companies page's own add form", async () => {
    renderActions();

    await userEvent.click(screen.getByRole("button", { name: /Add company/ }));

    expect(screen.getByTestId("landed")).toHaveTextContent("/projects/p1/companies/universe?add=company");
  });

  it("opens the picked executive's drawer on Notes by its link, not by the remembered tab", async () => {
    renderActions();

    await userEvent.click(screen.getByRole("button", { name: /Write a note/ }));
    await userEvent.click(await screen.findByRole("button", { name: /^Executive 3(?!\d)/ }));

    expect(screen.getByTestId("landed")).toHaveTextContent("/projects/p1/companies/universe?view=pipeline&candidate=c3&tab=notes");
    expect(sessionStorage.getItem("lm.person-drawer.tab.position")).toBeNull();
  });

  it("lists thirty executives and asks for a few letters for the rest", async () => {
    renderActions();

    await userEvent.click(screen.getByRole("button", { name: /Book a call/ }));

    expect(await screen.findByText("Keep typing to narrow — 4 more")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^Executive \d+/ })).toHaveLength(30);
  });
});
