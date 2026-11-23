import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import * as evalApi from "../api/evalApi";
import type { EvalDatasetDetail } from "../api/types";
import EvalCalibrationPage from "./EvalCalibrationPage";
import { dataset } from "./evalFixtures";
import { calibrationPair, calibrationPairs, rubric } from "./judgeFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  getRubricPairs: vi.fn(),
  labelPair: vi.fn(),
  checkJudge: vi.fn(),
}));

function WhereAmI() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/platform/evals/datasets/d1/calibration"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/evals/datasets/:datasetId/calibration" element={<EvalCalibrationPage />} />
            <Route path="*" element={<WhereAmI />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const pick = (user: ReturnType<typeof userEvent.setup>, criterion: string, option: string) =>
  user.click(within(screen.getByRole("radiogroup", { name: criterion })).getByRole("radio", { name: option }));

describe("EvalCalibrationPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.getRubricPairs).mockResolvedValue(calibrationPairs());
    vi.mocked(evalApi.labelPair).mockResolvedValue(undefined);
  });

  it("opens on the first pair still to label, showing both answers and never which version wrote them", async () => {
    renderPage();

    expect(await screen.findByText("Pair 2 of 3")).toBeInTheDocument();
    expect(screen.getByText("1 labelled")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Pairs labelled" })).toHaveAttribute("aria-valuenow", "1");
    expect(screen.getByText("AD-002")).toBeInTheDocument();
    const answerA = screen.getByRole("region", { name: "Answer A" });
    expect(within(answerA).getByText("Financial stewardship")).toBeInTheDocument();
    expect(within(answerA).getByText("8")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Answer B" })).getByText("No evidence")).toBeInTheDocument();
    expect(screen.queryByText(/candidate|live version/i)).not.toBeInTheDocument();

    expect(screen.queryByLabelText("The case's input")).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Show the full input" }));
    expect(screen.getByLabelText("The case's input")).toHaveTextContent("Chief financial officer");
  });

  it("saves a pair once every criterion has a pick, and moves to the next", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Pair 2 of 3");

    const save = screen.getByRole("button", { name: "Save and next" });
    expect(save).toBeDisabled();
    await pick(user, "Evidence is specific and names its source", "B better");
    expect(save).toBeDisabled();
    await pick(user, "No invented facts", "Tie");
    expect(within(screen.getByRole("radiogroup", { name: "No invented facts" })).getByRole("radio", { name: "Tie" })).toBeChecked();
    await user.click(save);

    await waitFor(() => expect(evalApi.labelPair).toHaveBeenCalledWith("p2", { specific: "B", invented: "TIE" }));
    expect(await screen.findByText("Pair 3 of 3")).toBeInTheDocument();
    expect(screen.getByText("2 labelled")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save and finish" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Previous" }));
    expect(await screen.findByText("Pair 2 of 3")).toBeInTheDocument();
    expect(
      within(screen.getByRole("radiogroup", { name: "Evidence is specific and names its source" })).getByRole("radio", { name: "B better" }),
    ).toBeChecked();
  });

  it("after the last pair, checks the judge against the labels and returns to the rubric", async () => {
    const user = userEvent.setup();
    vi.mocked(evalApi.getRubricPairs).mockResolvedValue(
      calibrationPairs({ pairs: [calibrationPair(1, { picks: { specific: "A", invented: "TIE" } }), calibrationPair(2)] }),
    );
    const checking: EvalDatasetDetail = { dataset: dataset(), cases: [], rubric: rubric({ state: "CHECKING", checkJudged: 0 }) };
    vi.mocked(evalApi.checkJudge).mockResolvedValue(checking);
    renderPage();
    await screen.findByText("Pair 2 of 2");

    await pick(user, "Evidence is specific and names its source", "A better");
    await pick(user, "No invented facts", "A better");
    await user.click(screen.getByRole("button", { name: "Save and finish" }));

    expect(await screen.findByText("All 2 pairs are labelled")).toBeInTheDocument();
    expect(screen.getByText(/at 80% or more, the judged criteria count/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Check the judge" }));

    await waitFor(() => expect(evalApi.checkJudge).toHaveBeenCalledWith("d1"));
    expect(await screen.findByTestId("location")).toHaveTextContent("/platform/evals/datasets/d1?view=rubric");
  });

  it("opens on the first pair to review them when every pair is labelled", async () => {
    vi.mocked(evalApi.getRubricPairs).mockResolvedValue(
      calibrationPairs({
        pairs: [1, 2].map((position) => calibrationPair(position, { picks: { specific: "A", invented: "TIE" } })),
      }),
    );
    renderPage();

    expect(await screen.findByText("Pair 1 of 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save and next" })).toBeEnabled();
  });

  it("says so when no pairs have been drawn", async () => {
    vi.mocked(evalApi.getRubricPairs).mockResolvedValue(calibrationPairs({ pairs: [] }));
    renderPage();

    expect(await screen.findByText(/No pairs have been drawn for this rubric yet/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Assessment draft: pack v1" })).toHaveAttribute(
      "href",
      "/platform/evals/datasets/d1?view=rubric",
    );
  });
});
