import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import type { PlatformAction } from "../../../auth/api/types";
import * as promptApi from "../../prompts/api/promptApi";
import { model } from "../../prompts/pages/promptFixtures";
import * as evalApi from "../api/evalApi";
import EvalRunPage from "./EvalRunPage";
import { changedCases, runDetail, runOverview, summary } from "./evalFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  getRun: vi.fn(),
  getRunCases: vi.fn(),
  cancelRun: vi.fn(),
}));
vi.mock("../../prompts/api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../prompts/api/promptApi")>()),
  listAiModels: vi.fn(),
}));

const held = vi.hoisted(() => ({ actions: [] as string[] }));
vi.mock("../../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { platformActions: held.actions } }),
}));

const renderPage = (actions: PlatformAction[] = ["EVAL_RUN", "PROMPT_MANAGE", "PROMPT_PROMOTE"]) => {
  held.actions = actions;
  return render(
    <MemoryRouter initialEntries={["/platform/evals/runs/r42"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/evals/runs/:runId" element={<EvalRunPage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
};

const going = () =>
  runDetail({
    run: runOverview({ status: "RUNNING", verdict: null, callsMade: 41, finishedAt: null }),
    secondsLeft: 170,
    summary: null,
  });

const rowOf = (table: HTMLElement, text: string) => within(table).getByText(text).closest('[role="row"]') as HTMLElement;

describe("EvalRunPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.getRun).mockResolvedValue(runDetail());
    vi.mocked(evalApi.getRunCases).mockResolvedValue({ changed: changedCases(), unchanged: 10 });
    vi.mocked(promptApi.listAiModels).mockResolvedValue([
      model(),
      model({ modelId: "gemini-3.8-flash", displayName: "Gemini 3.8 Flash" }),
    ]);
  });

  it("compares the two versions check by check, with what the run means for putting the candidate live", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Assessment draft · v3 against v2 (live)" })).toBeInTheDocument();
    expect(screen.getByText("No worse")).toBeInTheDocument();
    expect(screen.getByText(/^R-0042 · Holdout split · 12 cases × 3 repeats · started by Noor Rahman, 19 Nov 2026/)).toBeInTheDocument();
    expect(
      screen.getByText(
        "Across the holdout cases, the candidate is no worse than the live version. One case regressed, inside the tolerance; it is listed first below. The candidate can be put live without a written reason.",
      ),
    ).toBeInTheDocument();

    const checks = screen.getByRole("table", { name: "Checks" });
    expect(within(checks).getByRole("columnheader", { name: "Live" })).toBeInTheDocument();
    const better = rowOf(checks, "Evidence names its source");
    expect(within(better).getByText("97.2%")).toBeInTheDocument();
    expect(within(better).getByText("88.9%")).toBeInTheDocument();
    expect(within(better).getByText("+8.3 pts")).toBeInTheDocument();
    expect(within(rowOf(checks, "Every competency id is valid")).getByText("Same")).toBeInTheDocument();
    expect(within(rowOf(checks, "Score inside the labelled band")).getByText("−2.8 pts")).toBeInTheDocument();
    expect(
      await screen.findByText(
        /Score spread across the 3 repeats: ±0.4 for the candidate, ±0.6 for the live version. Candidate on Gemini 3.8 Flash, temperature 0.2 · Live on Gemini 3.5 Flash, temperature 0. 72 calls · 0.61M tokens./,
      ),
    ).toBeInTheDocument();
  });

  it("lists the cases that changed, regressions first, and opens one to both versions' scores and evidence", async () => {
    const user = userEvent.setup();
    renderPage();

    const regressed = await screen.findByRole("button", { name: /AD-007/ });
    const better = screen.getByRole("button", { name: /AD-022/ });
    expect(regressed.compareDocumentPosition(better) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(regressed).toHaveTextContent("Regression");
    expect(regressed).toHaveTextContent("Financial stewardship fell from 8 to 6. The labelled band is 7 to 9.");
    expect(better).toHaveTextContent("Better");
    expect(screen.getByText("10 of the 12 holdout cases were answered the same by both versions and are not listed.")).toBeInTheDocument();
    expect(regressed).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("7 to 9")).not.toBeInTheDocument();

    await user.click(regressed);

    expect(regressed).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("7 to 9")).toBeInTheDocument();
    expect(screen.getByText("6.0 ± 0.0")).toBeInTheDocument();
    expect(screen.getByText("8.0 ± 0.5")).toBeInTheDocument();
    expect(screen.getByText("The 2024 note says the refinancing stalled (note, 14 Feb 2024).")).toBeInTheDocument();
    expect(screen.getByText("Closed the refinancing in March 2026 (call 9 Oct, 22:10).")).toBeInTheDocument();
    expect(screen.getAllByText("No evidence")).toHaveLength(3);
    expect(screen.getByText(/Scores are the mean of 3 repeats, with their spread/)).toBeInTheDocument();

    // A prompt that scores nothing shows the checks that moved instead.
    await user.click(better);
    expect(screen.getByText("Candidate: 3 of 3 pass")).toBeInTheDocument();
    expect(screen.getByText("Live: 1 of 3 pass")).toBeInTheDocument();
  });

  it("marks a failed run's checks that fell past the tolerance", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(
      runDetail({
        run: runOverview({ verdict: "FAIL" }),
        summary: summary({
          checks: [
            { id: "score-in-band", label: "Score inside the labelled band", candidate: 75, baseline: 83.3, change: -8.3, worseBeyondTolerance: true },
          ],
        }),
      }),
    );
    renderPage();

    expect(await screen.findByText("Regressed")).toBeInTheDocument();
    expect(
      screen.getByText(
        "The candidate is worse than the live version on one check. Putting it live needs a written reason, which is recorded.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Outside the tolerance of 3 points")).toBeInTheDocument();
  });

  it("sends the candidate to its prompt's page to be put live, for someone who may", async () => {
    renderPage();

    expect(await screen.findByRole("link", { name: "Put the candidate live…" })).toHaveAttribute(
      "href",
      "/platform/prompts/candidate-scorecard-draft?version=3&putLive=1",
    );
  });

  it("offers no put live to someone who may only run evals", async () => {
    renderPage(["EVAL_RUN"]);

    await screen.findByRole("table", { name: "Checks" });
    expect(screen.queryByRole("link", { name: "Put the candidate live…" })).not.toBeInTheDocument();
  });

  it("shows a run's progress while it goes, and cancels it", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(going());
    vi.mocked(evalApi.cancelRun).mockResolvedValue(
      runDetail({ run: runOverview({ status: "CANCELLED", verdict: null, callsMade: 41 }), summary: null }),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("41 of 72 calls · about 3 minutes left")).toBeInTheDocument();
    const bar = screen.getByRole("progressbar", { name: "Calls made" });
    expect(bar).toHaveAttribute("aria-valuenow", "41");
    expect(bar).toHaveAttribute("aria-valuemax", "72");
    expect(screen.getByText(/It keeps going if you leave this page/)).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Checks" })).not.toBeInTheDocument();
    expect(evalApi.getRunCases).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Cancel run" }));

    await waitFor(() => expect(evalApi.cancelRun).toHaveBeenCalledWith("r42"));
    expect(await screen.findByText("Run R-0042 cancelled. The answers it has are kept")).toBeInTheDocument();
    expect(
      await screen.findByText(
        "This run was cancelled after 41 of 72 calls. A cancelled run gives no result, so it cannot support putting a version live.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Put the candidate live…" })).not.toBeInTheDocument();
  });

  it("says plainly that a run which stopped gives no result", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(
      runDetail({ run: runOverview({ status: "FAILED", verdict: null, failure: "MODEL_UNAVAILABLE" }), summary: null }),
    );
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The model did not answer, so this run stopped. It gives no result, so it cannot support putting a version live.",
    );
    expect(screen.queryByRole("table", { name: "Checks" })).not.toBeInTheDocument();
  });
});
