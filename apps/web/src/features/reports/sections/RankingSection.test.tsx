import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { RankedCandidate, Ranking, RankingCriterion } from "../api/types";
import { RankingSection } from "./RankingSection";

vi.mock("../api/reportApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getRanking: vi.fn(),
  saveRankingScenario: vi.fn(),
}));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u-lead", workspace: { roles: ["ADMIN"] } } }),
}));

const TREASURY: RankingCriterion = { key: "TECHNICAL:treasury", panel: "technical", name: "Treasury", description: null, weight: 60 };
const REPORTING: RankingCriterion = { key: "TECHNICAL:reporting", panel: "technical", name: "Reporting", description: null, weight: 40 };
const LEADERSHIP: RankingCriterion = { key: "BEHAVIOURAL:leadership", panel: "behavioural", name: "Leadership", description: null, weight: 100 };

function candidate(
  id: string,
  fullName: string,
  scores: [number | null, number | null, number | null],
  status: "draft" | "reviewed" = "reviewed",
): RankedCandidate {
  return {
    candidateId: id,
    personId: `person-${id}`,
    fullName,
    title: null,
    companyName: null,
    companyLogoUrl: null,
    enrichedAt: null,
    statusLabel: null,
    statusColour: null,
    stageLabel: null,
    outOfProcess: false,
    processingRestricted: false,
    assessment: {
      status,
      updatedAt: null,
      reviewedAt: null,
      reviewedByName: null,
      baselineOverall: 0,
      scoredCount: scores.filter((score) => score !== null).length,
      criteriaCount: 3,
      retiredCount: 0,
      scores: [TREASURY, REPORTING, LEADERSHIP].map((criterion, index) => ({
        key: criterion.key,
        score: scores[index],
        hasEvidence: false,
      })),
    },
  };
}

const RANKING: Ranking = {
  criteria: [TREASURY, REPORTING, LEADERSHIP],
  baselineTechnicalShare: 50,
  candidates: [
    candidate("marcus", "Marcus Chen", [10, 10, 7]),
    candidate("asha", "Asha Mehta", [6, 6, 10]),
    candidate("omar", "Omar Haddad", [5, 5, 5], "draft"),
  ],
  notAssessedCount: 2,
  mappedCount: 5,
  truncated: false,
  scenario: null,
};

const project = { id: "p1", team: [], representatives: [] } as unknown as Project;

const renderSection = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <RankingSection project={project} />
      </QueryClientProvider>
    </MemoryRouter>,
  );

const namesInOrder = () =>
  within(screen.getByRole("table", { name: "Candidate ranking" }))
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0].textContent);

/**
 * The Ranking chapter: the assessed in order at the brief's weighting, re-ranked live as the split moves, two compared
 * side by side, the scenario saved with the version it opened — and a refused read stated, never an empty ranking.
 */
describe("RankingSection", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
  });

  it("ranks at the brief's weighting and compares the top two", async () => {
    vi.mocked(reportApi.getRanking).mockResolvedValue(RANKING);

    renderSection();

    expect(await screen.findByText("Marcus leads under these priorities")).toBeInTheDocument();
    expect(namesInOrder()[0]).toMatch(/Marcus Chen/);
    expect(namesInOrder()[1]).toMatch(/Asha Mehta/);
    expect(screen.getByText(/3 candidates · Baseline vs scenario · 2 not assessed/)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Shortlist comparison" })).toBeInTheDocument();
    expect(screen.getAllByText("Agreed baseline").length).toBeGreaterThan(0);
    expect(screen.queryByText("Exploration scenario")).not.toBeInTheDocument();
  });

  it("re-ranks as the technical split moves, and marks the change unsaved", async () => {
    vi.mocked(reportApi.getRanking).mockResolvedValue(RANKING);
    renderSection();
    await screen.findByText("Marcus leads under these priorities");

    fireEvent.change(screen.getByRole("slider", { name: "Technical weight" }), { target: { value: "20" } });

    expect(namesInOrder()[0]).toMatch(/Asha Mehta/);
    expect(screen.getAllByText("Up 1").length).toBeGreaterThan(0);
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(screen.getByText("Exploration scenario")).toBeInTheDocument();
  });

  it("holds the save while a changed group does not total 100", async () => {
    vi.mocked(reportApi.getRanking).mockResolvedValue(RANKING);
    renderSection();
    await screen.findByText("Marcus leads under these priorities");

    fireEvent.change(screen.getByRole("slider", { name: "Treasury" }), { target: { value: "70" } });

    expect(screen.getByRole("alert")).toHaveTextContent("Each group you change must total 100%");
    expect(screen.getByRole("button", { name: "Save scenario" })).toBeDisabled();
  });

  it("saves the scenario from its dialog with the version it opened, and offers a reload keeping mine when a teammate saved first", async () => {
    vi.mocked(reportApi.getRanking).mockResolvedValue(RANKING);
    vi.mocked(reportApi.saveRankingScenario).mockRejectedValueOnce(
      new ApiRequestError({ code: "RANKING_SCENARIO_STALE", detail: "stale", status: 409, correlationId: "x" }),
    );
    const user = userEvent.setup();
    renderSection();
    await screen.findByText("Marcus leads under these priorities");

    fireEvent.change(screen.getByRole("slider", { name: "Technical weight" }), { target: { value: "70" } });
    await user.click(screen.getByRole("button", { name: "Save scenario" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Save" }));

    expect(reportApi.saveRankingScenario).toHaveBeenCalledWith("p1", {
      version: null,
      technicalShare: 70,
      weights: { [TREASURY.key]: 60, [REPORTING.key]: 40, [LEADERSHIP.key]: 100 },
      rationale: null,
    });
    expect(await screen.findByText("Someone saved this scenario after you opened it.")).toBeInTheDocument();

    vi.mocked(reportApi.getRanking).mockResolvedValue({
      ...RANKING,
      scenario: {
        version: 4,
        technicalShare: 30,
        weights: { [TREASURY.key]: 60, [REPORTING.key]: 40, [LEADERSHIP.key]: 100 },
        rationale: null,
        updatedAt: "2026-11-10T09:00:00Z",
        updatedByName: "Sara Al-Mansour",
        briefChangedSinceSaved: false,
        droppedCriteria: [],
      },
    });
    vi.mocked(reportApi.saveRankingScenario).mockResolvedValueOnce(null);
    await user.click(screen.getByRole("button", { name: "Reload, keeping mine" }));
    await waitFor(() => expect(screen.queryByText("Someone saved this scenario after you opened it.")).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Save scenario" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Save" }));

    expect(reportApi.saveRankingScenario).toHaveBeenLastCalledWith("p1", expect.objectContaining({ version: 4, technicalShare: 70 }));
  });

  it("narrows to reviewed assessments, re-ranking within them", async () => {
    vi.mocked(reportApi.getRanking).mockResolvedValue(RANKING);
    const user = userEvent.setup();
    renderSection();
    await screen.findByText("Marcus leads under these priorities");

    await user.click(screen.getByRole("button", { name: "Reviewed only" }));

    expect(namesInOrder()).toHaveLength(2);
    expect(screen.queryByText(/Omar Haddad/, { selector: "span" })).not.toBeInTheDocument();
  });

  it("states a refused read instead of an empty ranking", async () => {
    vi.mocked(reportApi.getRanking).mockRejectedValue(new Error("forbidden"));

    renderSection();

    expect(await screen.findByText("Couldn't load the ranking")).toBeInTheDocument();
    expect(screen.queryByText(/none assessed yet/)).not.toBeInTheDocument();
  });
});
