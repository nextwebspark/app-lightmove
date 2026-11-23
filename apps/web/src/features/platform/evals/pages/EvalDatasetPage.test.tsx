import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import * as evalApi from "../api/evalApi";
import type { EvalCaseImportResult, EvalDatasetDetail } from "../api/types";
import EvalDatasetPage from "./EvalDatasetPage";
import { dataset, evalCase } from "./evalFixtures";

vi.mock("../api/evalApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/evalApi")>()),
  getDataset: vi.fn(),
  retireCase: vi.fn(),
  previewCaseImport: vi.fn(),
  commitCaseImport: vi.fn(),
}));

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/platform/evals/datasets/d1"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/evals/datasets/:datasetId" element={<EvalDatasetPage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const DETAIL: EvalDatasetDetail = {
  dataset: dataset(),
  cases: [
    evalCase(),
    evalCase({ id: "c4", key: "AD-004", name: "Self-report only", tag: "self-report", split: "HOLDOUT", addedBy: null }),
    evalCase({ id: "c12", key: "AD-012", name: "Two executives on one call", tag: "speaker", retiredAt: "2026-11-18T10:00:00Z" }),
  ],
  rubric: null,
};

const plan = (overrides: Partial<EvalCaseImportResult> = {}): EvalCaseImportResult => ({
  committed: false,
  rows: 31,
  added: 28,
  alreadyPresent: 3,
  dev: 17,
  holdout: 11,
  problems: [],
  ...overrides,
});

const file = () => new File(['{"key":"AD-031"}'], "assessment-draft-pack-v1b.jsonl", { type: "application/jsonl" });

const rowOf = (key: string) => screen.getByText(key).closest('[role="row"]') as HTMLElement;

describe("EvalDatasetPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(evalApi.getDataset).mockResolvedValue(DETAIL);
  });

  it("lists the dataset's cases with their split and who added them", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Assessment draft: pack v1" })).toBeInTheDocument();
    expect(screen.getByText("Assessment draft · 30 cases · 18 dev · 12 holdout")).toBeInTheDocument();
    expect(within(rowOf("AD-001")).getByText("Strong direct evidence")).toBeInTheDocument();
    expect(within(rowOf("AD-001")).getByText("Dev")).toBeInTheDocument();
    expect(within(rowOf("AD-001")).getByText("Noor Rahman")).toBeInTheDocument();
    expect(within(rowOf("AD-004")).getByText("Holdout")).toBeInTheDocument();
    expect(within(rowOf("AD-004")).getByText("Shipped")).toBeInTheDocument();
    expect(within(rowOf("AD-012")).getByText("Retired")).toBeInTheDocument();
    expect(within(rowOf("AD-012")).queryByRole("button", { name: /Retire/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Datasets" })).toHaveAttribute("href", "/platform/evals");
  });

  it("retires a case, which stays listed as retired", async () => {
    vi.mocked(evalApi.retireCase).mockResolvedValue({
      ...DETAIL,
      cases: DETAIL.cases.map((listed) => (listed.id === "c1" ? { ...listed, retiredAt: "2026-11-21T09:00:00Z" } : listed)),
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Retire AD-001" }));

    await waitFor(() => expect(evalApi.retireCase).toHaveBeenCalledWith("c1"));
    expect(await screen.findByText("Case retired. Runs that scored it still name it")).toBeInTheDocument();
    await waitFor(() => expect(within(rowOf("AD-001")).getByText("Retired")).toBeInTheDocument());
  });

  it("reads an import file first, then adds its new cases", async () => {
    vi.mocked(evalApi.previewCaseImport).mockResolvedValue(plan());
    vi.mocked(evalApi.commitCaseImport).mockResolvedValue(plan({ committed: true }));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Import cases" }));
    const dialog = await screen.findByRole("dialog", { name: "Import cases" });
    expect(within(dialog).getByRole("button", { name: "Add cases" })).toBeDisabled();
    const chosen = file();
    await user.upload(within(dialog).getByLabelText("Choose a file of cases"), chosen);

    expect(
      await within(dialog).findByText(
        "28 new cases, 3 already in the dataset. 17 dev, 11 holdout. Every row has the right shape.",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("assessment-draft-pack-v1b.jsonl")).toBeInTheDocument();
    expect(within(dialog).getByText("31 rows · choose another file")).toBeInTheDocument();
    expect(evalApi.commitCaseImport).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "Add 28 cases" }));

    await waitFor(() => expect(evalApi.commitCaseImport).toHaveBeenCalledWith("d1", chosen));
    expect(await screen.findByText("28 cases added to Assessment draft: pack v1")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Import cases" })).not.toBeInTheDocument());
  });

  it("names every row at fault by its line, and adds nothing while one is", async () => {
    vi.mocked(evalApi.previewCaseImport).mockResolvedValue(
      plan({
        added: 0,
        problems: [
          { line: 14, message: "notes[2].date is missing. Every note carries its date." },
          { line: 22, message: "source is not SYNTHETIC. Only synthetic cases can be imported." },
          { line: 22, message: "split is missing." },
        ],
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Import cases" }));
    const dialog = await screen.findByRole("dialog", { name: "Import cases" });
    await user.upload(within(dialog).getByLabelText("Choose a file of cases"), file());

    const alert = await within(dialog).findByRole("alert");
    expect(within(alert).getByText("2 rows cannot be imported. Fix the file and choose it again.")).toBeInTheDocument();
    expect(within(alert).getByText("Row 14")).toBeInTheDocument();
    expect(within(alert).getByText("notes[2].date is missing. Every note carries its date.")).toBeInTheDocument();
    expect(within(alert).getAllByText("Row 22")).toHaveLength(2);
    expect(within(dialog).getByRole("button", { name: "Add cases" })).toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: /choose another file/ }));
    expect(within(dialog).getByLabelText("Choose a file of cases")).toBeInTheDocument();
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
  });

  it("offers no import for a prompt whose cases cannot be added to", async () => {
    vi.mocked(evalApi.getDataset).mockResolvedValue({ ...DETAIL, dataset: dataset({ importable: false }) });
    renderPage();

    await screen.findByRole("heading", { name: "Assessment draft: pack v1" });
    expect(screen.queryByRole("button", { name: "Import cases" })).not.toBeInTheDocument();
  });
});
