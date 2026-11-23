import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import * as promptApi from "../../prompts/api/promptApi";
import { model } from "../../prompts/pages/promptFixtures";
import * as evalApi from "../api/evalApi";
import EvalRunPage from "../pages/EvalRunPage";
import { changedCases, runDetail, runOverview, summary } from "../pages/evalFixtures";
import { judged, judgedCase } from "../pages/judgeFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  getRun: vi.fn(),
  getRunCases: vi.fn(),
}));
vi.mock("../../prompts/api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../prompts/api/promptApi")>()),
  listAiModels: vi.fn(),
}));
vi.mock("../../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { platformActions: ["EVAL_RUN", "PROMPT_MANAGE", "PROMPT_PROMOTE"] } }),
}));

const renderPage = () =>
  render(
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

const rowOf = (table: HTMLElement, text: string) => within(table).getByText(text).closest('[role="row"]') as HTMLElement;

/** A run of the opener, which has no checks: the judge alone scores it. */
const judgeOnly = (verdict: "PASS" | null, state: "COUNTS" | "UNLABELLED") =>
  runDetail({
    run: runOverview({ promptId: "outreach-opener", promptName: "Outreach opener", judged: true, verdict, cases: 10 }),
    summary: summary({
      checks: [],
      regressedCases: 0,
      betterCases: 1,
      unchangedCases: 9,
      judged: judged({
        pairs: 30,
        state,
        counted: state === "COUNTS",
        agreementPercent: state === "COUNTS" ? 87.5 : null,
        criteria: [
          { key: "specific", name: "Specific to the person", candidateWon: 14, baselineWon: 6, ties: 10, unjudged: 0, net: 26.7, worseBeyondTolerance: false },
          { key: "plain", name: "Plain and brief", candidateWon: 9, baselineWon: 11, ties: 10, unjudged: 0, net: -6.7, worseBeyondTolerance: false },
        ],
      }),
    }),
  });

describe("a run's judged criteria", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.getRunCases).mockResolvedValue({ changed: changedCases(), unchanged: 10 });
    vi.mocked(promptApi.listAiModels).mockResolvedValue([
      model(),
      model({ modelId: "gemini-3.8-flash", displayName: "Gemini 3.8 Flash" }),
    ]);
  });

  it("lists each criterion's pairs beside the checks, and says they count", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(
      runDetail({ run: runOverview({ judged: true }), summary: summary({ judged: judged() }) }),
    );
    renderPage();

    const table = await screen.findByRole("table", { name: "Judged criteria" });
    expect(screen.getByRole("table", { name: "Checks" })).toBeInTheDocument();
    expect(screen.getByText("Counts")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Across the holdout cases, the candidate is no worse than the live version on the 3 checks and the 2 judged criteria. One case regressed, inside the tolerance; it is listed first below. The candidate can be put live without a written reason.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/When this run finished, the judge agreed with a person on 88% of the labelled picks, so these criteria count/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open the rubric" })).toHaveAttribute("href", "/platform/evals/datasets/d1?view=rubric");
    const specific = rowOf(table, "Evidence is specific and names its source");
    expect(within(specific).getByText("19")).toBeInTheDocument();
    expect(within(specific).getByText("6")).toBeInTheDocument();
    expect(within(specific).getByText("11")).toBeInTheDocument();
    expect(within(specific).getByText("+36 pts")).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Live better" })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText(/^36 pairs: each answer of the candidate beside the live version's answer/)).toHaveTextContent(
        "Eval judge v2 on Gemini 3.8 Flash, temperature 0 · rubric v1.",
      ),
    );
  });

  it("marks a criterion past the tolerance on a run it failed", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(
      runDetail({
        run: runOverview({ judged: true, verdict: "FAIL" }),
        summary: summary({
          judged: judged({
            criteria: [
              { key: "specific", name: "Evidence is specific and names its source", candidateWon: 4, baselineWon: 13, ties: 19, unjudged: 0, net: -25, worseBeyondTolerance: true },
            ],
          }),
        }),
      }),
    );
    renderPage();

    const table = await screen.findByRole("table", { name: "Judged criteria" });
    const worse = rowOf(table, "Evidence is specific and names its source");
    expect(within(worse).getByText("−25 pts")).toBeInTheDocument();
    expect(within(worse).getByText("Outside the tolerance of 10 points")).toBeInTheDocument();
    expect(
      screen.getByText(
        "The candidate is worse than the live version on one judged criterion. Putting it live needs a written reason, which is recorded.",
      ),
    ).toBeInTheDocument();
  });

  it("shows the criteria without counting them while the rubric does not count", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(
      runDetail({
        run: runOverview({ judged: true }),
        summary: summary({
          judged: judged({
            state: "SHOWN_ONLY",
            counted: false,
            agreementPercent: 70,
            criteria: [
              { key: "specific", name: "Evidence is specific and names its source", candidateWon: 4, baselineWon: 13, ties: 19, unjudged: 0, net: -25, worseBeyondTolerance: true },
            ],
          }),
        }),
      }),
    );
    renderPage();

    const table = await screen.findByRole("table", { name: "Judged criteria" });
    expect(screen.getByText("Shown only")).toBeInTheDocument();
    expect(screen.getByText(/70% of the labelled picks, below the 80% needed\. These criteria are shown and do not count/)).toBeInTheDocument();
    expect(within(table).queryByText(/Outside the tolerance/)).not.toBeInTheDocument();
    expect(screen.getByText(/no worse than the live version on the 3 checks\. The judged criteria are shown and do not count\./)).toBeInTheDocument();
  });

  it("gives no result for a prompt with no checks whose judged criteria do not count", async () => {
    vi.mocked(evalApi.getRun).mockResolvedValue(judgeOnly(null, "UNLABELLED"));
    vi.mocked(evalApi.getRunCases).mockResolvedValue({ changed: [judgedCase()], unchanged: 9 });
    renderPage();

    expect(await screen.findByText("No result")).toBeInTheDocument();
    expect(
      screen.getByText(
        "This run gives no result. Outreach opener has no checks, and its judged criteria do not count until a person has labelled 20 pairs and the judge has been checked against them. Putting the candidate live needs a written reason, which is recorded.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Checks" })).not.toBeInTheDocument();
    expect(screen.getByText("Outreach opener has no checks. The judge alone scores it.")).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Judged criteria" })).toBeInTheDocument();
    expect(
      await screen.findByText("9 of the 10 holdout cases were judged a tie on every criterion and are not listed."),
    ).toBeInTheDocument();
  });

  it("opens a judged case to both answers as written, the judge's picks and the order it saw them in", async () => {
    const user = userEvent.setup();
    vi.mocked(evalApi.getRun).mockResolvedValue(judgeOnly("PASS", "COUNTS"));
    vi.mocked(evalApi.getRunCases).mockResolvedValue({ changed: [judgedCase()], unchanged: 9 });
    renderPage();

    expect(
      await screen.findByText(
        "Across the holdout cases, the candidate is no worse than the live version on the 2 judged criteria. The candidate can be put live without a written reason.",
      ),
    ).toBeInTheDocument();
    const opened = await screen.findByRole("button", { name: /OP-014/ });
    expect(opened).toHaveTextContent("Specific to the person: the judge preferred the candidate on 3 of 3 pairs.");
    await user.click(opened);

    expect(screen.getByText("You moved from Aldermoor Power to lead finance at Kestrel Grid in September.")).toBeInTheDocument();
    expect(screen.getByText("Your impressive career in energy finance caught my attention.")).toBeInTheDocument();
    expect(screen.getByText("The first of 3 repeats. The case's input is fictional; open it in its dataset.")).toBeInTheDocument();
    expect(screen.getByText("Candidate better · 3 of 3 pairs")).toBeInTheDocument();
    expect(screen.getByText("Tie · 2 of 3 pairs")).toBeInTheDocument();
    expect(screen.getByText("B names the move to Kestrel Grid in September.")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Order shown to the judge, recorded with each pair: pair 1, live as A and candidate as B · pair 2, candidate as A and live as B · pair 3, live as A and candidate as B. Each reason is the judge's own, on the first pair it gave one for.",
      ),
    ).toBeInTheDocument();
  });
});
