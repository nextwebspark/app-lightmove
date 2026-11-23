import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as promptApi from "../../prompts/api/promptApi";
import { model } from "../../prompts/pages/promptFixtures";
import * as evalApi from "../api/evalApi";
import type { EvalDatasetDetail, EvalRubricDetail } from "../api/types";
import EvalDatasetPage from "../pages/EvalDatasetPage";
import { dataset, evalCase } from "../pages/evalFixtures";
import { calibrationPairs, rubric, unlabelledRubric } from "../pages/judgeFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  getDataset: vi.fn(),
  startLabelling: vi.fn(),
  checkJudge: vi.fn(),
}));
vi.mock("../../prompts/api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../prompts/api/promptApi")>()),
  listAiModels: vi.fn(),
}));

function WhereAmI() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

const detailWith = (ofRubric: EvalRubricDetail | null): EvalDatasetDetail => ({
  dataset: dataset({ rubric: ofRubric ? { version: ofRubric.version, state: ofRubric.state } : null }),
  cases: [evalCase()],
  rubric: ofRubric,
});

const renderPage = (entry = "/platform/evals/datasets/d1?view=rubric") =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/evals/datasets/:datasetId" element={<EvalDatasetPage />} />
            <Route path="*" element={<WhereAmI />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("a dataset's Rubric view", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.getDataset).mockResolvedValue(detailWith(rubric()));
    vi.mocked(promptApi.listAiModels).mockResolvedValue([
      model({ modelId: "gemini-3.8-flash", displayName: "Gemini 3.8 Flash" }),
    ]);
  });

  it("is reached from the dataset's cases, and hides Import cases", async () => {
    const user = userEvent.setup();
    renderPage("/platform/evals/datasets/d1");

    expect(await screen.findByRole("table", { name: "Cases" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Cases" })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: "Rubric" }));

    expect(await screen.findByRole("table", { name: "Rubric criteria" })).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Cases" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Import cases" })).not.toBeInTheDocument();
  });

  it("says the judged criteria count, with the judge's agreement on each criterion and where it differs", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("The judged criteria count")).toBeInTheDocument();
    expect(screen.getByText("Counts")).toBeInTheDocument();
    expect(
      screen.getByText(
        "The judge agrees with a person on 35 of 40 picks (88%). At 80% or more, a run's judged criteria count toward its result, and so toward putting a version live.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("20 pairs labelled by Noor Rahman, 05 Nov 2026 · judge last checked 06 Nov 2026, as version 2")).toBeInTheDocument();
    const criteria = screen.getByRole("table", { name: "Rubric criteria" });
    expect(within(criteria).getByText("18 of 20")).toBeInTheDocument();
    expect(within(criteria).getByText("17 of 20")).toBeInTheDocument();
    expect(await screen.findByText(/The judge is the prompt Eval judge, version 2, on Gemini 3.8 Flash at temperature 0\./)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open its prompt" })).toHaveAttribute("href", "/platform/prompts/eval-judge");
    expect(screen.getByRole("link", { name: "Review labels" })).toHaveAttribute("href", "/platform/evals/datasets/d1/calibration");
    expect(screen.queryByRole("button", { name: /Check the judge/ })).not.toBeInTheDocument();

    expect(screen.getByText("4 of the 40 picks. The first 3 are listed.")).toBeInTheDocument();
    expect(screen.getByText("Person: B better · Judge: Tie")).toBeInTheDocument();
    expect(screen.getByText("The judge's reason: Both answers name the call.")).toBeInTheDocument();
    expect(screen.queryByText("AD-009")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Show all" }));
    expect(screen.getByText("AD-009")).toBeInTheDocument();
    expect(screen.getByText("Person: A better · Judge: No pick")).toBeInTheDocument();
  });

  it("says a judge below the threshold is shown only", async () => {
    vi.mocked(evalApi.getDataset).mockResolvedValue(
      detailWith(rubric({ state: "SHOWN_ONLY", agreed: 28, picks: 40, agreementPercent: 70 })),
    );
    renderPage();

    expect(await screen.findByText("The judged criteria are shown only")).toBeInTheDocument();
    expect(screen.getByText("Shown only")).toBeInTheDocument();
    expect(screen.getByText(/28 of 40 picks \(70%\), below the 80% needed/)).toBeInTheDocument();
  });

  it("starts labelling where nothing is labelled, and opens the pairs", async () => {
    const user = userEvent.setup();
    vi.mocked(evalApi.getDataset).mockResolvedValue(detailWith(unlabelledRubric()));
    vi.mocked(evalApi.startLabelling).mockResolvedValue(calibrationPairs());
    renderPage();

    expect(await screen.findByText(/No pairs are labelled yet\. Until a person has labelled 20 pairs/)).toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: "Rubric criteria" })).getAllByText("Not measured")).toHaveLength(2);
    expect(screen.queryByText("Where the judge and the person differ")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Label pairs" }));

    await waitFor(() => expect(evalApi.startLabelling).toHaveBeenCalledWith("d1"));
    expect(await screen.findByTestId("location")).toHaveTextContent("/platform/evals/datasets/d1/calibration");
  });

  it("offers no labelling until a run on the dev cases has finished, and says why", async () => {
    vi.mocked(evalApi.getDataset).mockResolvedValue(detailWith(unlabelledRubric({ pairsAvailable: false })));
    renderPage();

    expect(await screen.findByRole("button", { name: "Label pairs" })).toBeDisabled();
    expect(screen.getByText(/With no finished run on the dev cases, start one first\./)).toBeInTheDocument();
  });

  it("asks for the judge to be checked again once its live version has changed", async () => {
    const user = userEvent.setup();
    vi.mocked(evalApi.getDataset).mockResolvedValue(detailWith(rubric({ state: "STALE", checkedJudgeVersion: 1 })));
    vi.mocked(evalApi.checkJudge).mockResolvedValue(detailWith(rubric({ state: "CHECKING", checkJudged: 8 })));
    renderPage();

    expect(await screen.findByText("The judge changed, so the judged criteria are shown only")).toBeInTheDocument();
    expect(screen.getByText("Check again")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Agreement was 88% with version 1 of the judge. Version 2 is live now. Check it again on the 20 labelled pairs: nothing needs labelling again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Agreement, version 1" })).toBeInTheDocument();
    expect(screen.getByText(/Checking makes 20 calls/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Check the judge again" }));

    expect(await screen.findByText("Checking the judge against your labels")).toBeInTheDocument();
    expect(screen.getByText("8 of 20 pairs judged. It keeps going if you leave this page.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Check the judge/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Review labels" })).not.toBeInTheDocument();
  });

  it("says so when a check is refused", async () => {
    const user = userEvent.setup();
    vi.mocked(evalApi.getDataset).mockResolvedValue(
      detailWith(rubric({ state: "UNCHECKED", agreed: null, picks: null, agreementPercent: null, checkedAt: null, checkedJudgeVersion: null, differences: [] })),
    );
    vi.mocked(evalApi.checkJudge).mockRejectedValue(
      new ApiRequestError({ code: "EVAL_JUDGE_CHECK_IN_PROGRESS", detail: "", status: 409, correlationId: "c", fieldErrors: {} }),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Check the judge" }));

    expect(await screen.findByText("The judge is being checked already.")).toBeInTheDocument();
  });

  it("says a dataset with no rubric is scored by its checks alone", async () => {
    vi.mocked(evalApi.getDataset).mockResolvedValue(detailWith(null));
    renderPage();

    expect(await screen.findByText("This dataset has no rubric. Its runs are scored by its checks alone.")).toBeInTheDocument();
  });
});
