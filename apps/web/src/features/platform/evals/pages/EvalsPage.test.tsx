import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as promptApi from "../../prompts/api/promptApi";
import { detail as promptDetail } from "../../prompts/pages/promptFixtures";
import * as evalApi from "../api/evalApi";
import EvalsPage from "./EvalsPage";
import { dataset, estimate, runDetail, runOverview } from "./evalFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  listDatasets: vi.fn(),
  listRuns: vi.fn(),
  estimateRun: vi.fn(),
  startRun: vi.fn(),
}));
vi.mock("../../prompts/api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../prompts/api/promptApi")>()),
  getPrompt: vi.fn(),
}));

function Location() {
  const { pathname, search } = useLocation();
  return <div data-testid="location">{pathname + search}</div>;
}

const renderAt = (path = "/platform/evals") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/evals" element={<EvalsPage />} />
            <Route path="*" element={null} />
          </Routes>
          <Location />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const refusal = (code: string, status = 400, fieldErrors: Record<string, string> = {}) =>
  new ApiRequestError({ code, detail: "", status, correlationId: "c", fieldErrors });

const DATASETS = [
  dataset(),
  dataset({ id: "d2", name: "Nationality: synthetic profiles", promptId: "candidate-nationality", promptName: "Nationality", cases: 12, dev: 8, holdout: 4 }),
  dataset({ id: "d3", name: "Assistant: synthetic conversations", promptId: "assistant-core", promptName: "Assistant", importable: false }),
];

describe("EvalsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.listDatasets).mockResolvedValue(DATASETS);
    vi.mocked(evalApi.listRuns).mockResolvedValue([
      runOverview(),
      runOverview({ id: "r41", number: 41, verdict: "FAIL", startedBy: "Daniel Okafor" }),
      runOverview({ id: "r40", number: 40, promptName: "Nationality", split: "DEV", status: "CANCELLED", verdict: null, callsMade: 31, callsPlanned: 160 }),
    ]);
    vi.mocked(promptApi.getPrompt).mockResolvedValue(promptDetail());
    vi.mocked(evalApi.estimateRun).mockResolvedValue(estimate());
  });

  it("lists each dataset with its prompt, its cases and how they are split", async () => {
    renderAt();

    const table = await screen.findByRole("table", { name: "Datasets" });
    const row = within(table).getByRole("link", { name: "Assessment draft: pack v1" }).closest('[role="row"]') as HTMLElement;
    expect(within(row).getByText("Assessment draft")).toBeInTheDocument();
    expect(within(row).getByText("30")).toBeInTheDocument();
    expect(within(row).getByText("18 dev · 12 holdout")).toBeInTheDocument();
    expect(within(row).getByText("Synthetic")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Datasets" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("button", { name: "Start a run" })).not.toBeInTheDocument();
  });

  it("opens a dataset from its row, and the runs from their tab", async () => {
    const user = userEvent.setup();
    renderAt();

    await user.click(await screen.findByRole("tab", { name: "Runs" }));
    expect(screen.getByTestId("location").textContent).toBe("/platform/evals?tab=runs");
    expect(await screen.findByRole("table", { name: "Runs" })).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Datasets" }));
    await user.click(await screen.findByRole("link", { name: "Nationality: synthetic profiles" }));
    expect(screen.getByTestId("location").textContent).toBe("/platform/evals/datasets/d2");
  });

  it("lists the runs with what each found, what it cost and who started it", async () => {
    renderAt("/platform/evals?tab=runs");

    const table = await screen.findByRole("table", { name: "Runs" });
    const passed = within(table).getByRole("link", { name: "Open run R-0042" }).closest('[role="row"]') as HTMLElement;
    expect(within(passed).getByText("v3 against v2")).toBeInTheDocument();
    expect(within(passed).getByText("Holdout")).toBeInTheDocument();
    expect(within(passed).getByText("No worse")).toBeInTheDocument();
    expect(within(passed).getByText("72 calls · 0.61M tokens")).toBeInTheDocument();
    expect(within(passed).getByText(/^Noor Rahman · 19 Nov 2026/)).toBeInTheDocument();
    const failed = within(table).getByRole("link", { name: "Open run R-0041" }).closest('[role="row"]') as HTMLElement;
    expect(within(failed).getByText("Regressed")).toBeInTheDocument();
    const cancelled = within(table).getByRole("link", { name: "Open run R-0040" }).closest('[role="row"]') as HTMLElement;
    expect(within(cancelled).getByText("Cancelled")).toBeInTheDocument();
    expect(within(cancelled).getByText("31 of 160 calls")).toBeInTheDocument();
    expect(within(cancelled).queryByText("No worse")).not.toBeInTheDocument();
  });

  it("starts a run of the newest version against the live one, with what it would take shown first", async () => {
    vi.mocked(evalApi.startRun).mockResolvedValue(runDetail({ run: runOverview({ id: "r43", number: 43, status: "QUEUED" }) }));
    const user = userEvent.setup();
    renderAt("/platform/evals?tab=runs");

    await user.click(await screen.findByRole("button", { name: "Start a run" }));
    const dialog = await screen.findByRole("dialog", { name: "Start a run" });
    // Only a prompt whose cases can be run is offered.
    expect(within(dialog).getByRole("combobox", { name: "Prompt" })).toHaveValue("candidate-scorecard-draft");
    expect(within(dialog).queryByRole("option", { name: "Assistant" })).not.toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByRole("combobox", { name: "Candidate version" })).toHaveValue("3"));
    expect(within(dialog).getByRole("combobox", { name: "Baseline version" })).toHaveValue("2");
    expect(within(dialog).getByRole("option", { name: "v2 · live" })).toBeInTheDocument();
    expect(within(dialog).getByRole("option", { name: "Holdout · 12 cases" })).toBeInTheDocument();

    expect(await within(dialog).findByText("About 72 calls · 0.61M tokens")).toBeInTheDocument();
    expect(within(dialog).getByText("around 6 minutes")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "12 cases × 2 versions × 3 repeats. This prompt is always answered 3 times, because its answers vary. The run counts toward the AI budget and stops at 200 calls.",
      ),
    ).toBeInTheDocument();
    const asked = { datasetId: "d1", split: "HOLDOUT", candidateVersion: 3, baselineVersion: 2 };
    expect(vi.mocked(evalApi.estimateRun).mock.calls[0][0]).toEqual(asked);

    await user.click(within(dialog).getByRole("button", { name: "Start run" }));

    await waitFor(() => expect(evalApi.startRun).toHaveBeenCalledWith(asked));
    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/platform/evals/runs/r43"));
    expect(await screen.findByText("Run R-0043 started")).toBeInTheDocument();
  });

  it("will not start a run that is too large, or the same version against itself", async () => {
    vi.mocked(evalApi.estimateRun).mockResolvedValue(estimate({ calls: 202, tooLarge: true }));
    const user = userEvent.setup();
    renderAt("/platform/evals?tab=runs");

    await user.click(await screen.findByRole("button", { name: "Start a run" }));
    const dialog = await screen.findByRole("dialog", { name: "Start a run" });
    expect(await within(dialog).findByText(/more calls than one run may make/)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Start run" })).toBeDisabled();

    await user.selectOptions(within(dialog).getByRole("combobox", { name: "Baseline version" }), "3");
    expect(await within(dialog).findByText("Choose another version to compare it against")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Start run" })).toBeDisabled();
  });

  it("says why a start was refused: another run going, or a version the server will not take", async () => {
    vi.mocked(evalApi.startRun).mockRejectedValueOnce(refusal("EVAL_RUN_IN_PROGRESS", 409));
    vi.mocked(evalApi.startRun).mockRejectedValueOnce(
      refusal("PROMPT_MODEL_UNAVAILABLE", 409, { candidateVersion: "That version's model can no longer answer this prompt" }),
    );
    const user = userEvent.setup();
    renderAt("/platform/evals?tab=runs");

    await user.click(await screen.findByRole("button", { name: "Start a run" }));
    const dialog = await screen.findByRole("dialog", { name: "Start a run" });
    await within(dialog).findByText("About 72 calls · 0.61M tokens");

    await user.click(within(dialog).getByRole("button", { name: "Start run" }));
    expect(await within(dialog).findByText("Another run is going. Wait for it to end, or cancel it.")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Start run" }));
    expect(await within(dialog).findByText("That version's model can no longer answer this prompt")).toBeInTheDocument();
    expect(within(dialog).queryByText("Another run is going. Wait for it to end, or cancel it.")).not.toBeInTheDocument();
  });

  it("says a refused read was refused, never that there are no runs", async () => {
    vi.mocked(evalApi.listRuns).mockRejectedValue(refusal("INTERNAL_ERROR", 500));
    renderAt("/platform/evals?tab=runs");

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(/No runs yet/)).not.toBeInTheDocument();
  });

  it("asks a super admin with no second factor to set one up", async () => {
    vi.mocked(evalApi.listDatasets).mockRejectedValue(refusal("PLATFORM_TWO_FACTOR_REQUIRED", 403));
    renderAt();

    expect(await screen.findByText("Set up two-factor to use the platform console")).toBeInTheDocument();
  });
});
