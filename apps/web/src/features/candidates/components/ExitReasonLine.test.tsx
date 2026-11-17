import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aCurrentCatalog } from "../../../test/fixtures/pipelineCatalog";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason } from "../../workspace/api/types";
import * as candidatesApi from "../api/candidatesApi";
import type { CandidateExitReason } from "../api/types";
import { ExitReasonLine } from "./ExitReasonLine";

vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
  exitReasons: vi.fn(),
}));
vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/candidatesApi")>()),
  changeExitReason: vi.fn(),
}));

const catalog = aCurrentCatalog();
const notInterested = catalog.stages.flatMap((stage) => stage.statuses).find((status) => status.key === "notInterested")!;

function reason(id: string, label: string, extra: Partial<ExitReason> = {}): ExitReason {
  return { id, key: id, label, requiresNote: false, position: 1, retired: false, statusIds: [notInterested.id], ...extra };
}

function given(extra: Partial<CandidateExitReason> = {}): CandidateExitReason {
  return {
    candidateId: "c1",
    reasonId: "other",
    label: "Other",
    note: "Moving to Riyadh",
    requiresNote: true,
    retired: false,
    setAt: "2026-11-17T10:00:00Z",
    setByUserId: "u1",
    setByName: "Yara Haddad",
    ...extra,
  };
}

function renderLine(held: CandidateExitReason | null, variant: "line" | "pill" = "line") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, catalog);
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ExitReasonLine
          projectId="p1"
          candidateId="c1"
          name="Layla Haddad"
          statusKey="notInterested"
          given={held}
          canChange
          variant={variant}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("ExitReasonLine", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(catalog);
    vi.mocked(pipelineApi.exitReasons).mockResolvedValue({
      reasons: [reason("timing", "Timing"), reason("other", "Other", { requiresNote: true })],
    });
    vi.mocked(candidatesApi.changeExitReason).mockResolvedValue(given({ note: "Back to Dubai next year" }));
  });

  it("saves a reason picked in place, with no dialog for one that asks no note", async () => {
    renderLine(null);
    await screen.findByRole("option", { name: "Timing" });

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Why Layla Haddad left" }), "Timing");

    expect(candidatesApi.changeExitReason).toHaveBeenCalledWith("p1", "c1", { reasonId: "timing", note: null });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit note" })).not.toBeInTheDocument();
  });

  it("picks a reason from the drawer's pill in the status picker's own list", async () => {
    renderLine(null, "pill");

    await userEvent.click(await screen.findByRole("button", { name: "Why Layla Haddad left: Not given" }));
    await userEvent.click(await screen.findByRole("option", { name: "Timing" }));

    expect(candidatesApi.changeExitReason).toHaveBeenCalledWith("p1", "c1", { reasonId: "timing", note: null });
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("reopens a held reason's note, which the select alone cannot, and saves the corrected note", async () => {
    renderLine(given());
    await userEvent.click(await screen.findByRole("button", { name: "Edit note" }));

    expect(await screen.findByRole("dialog", { name: "Change why Layla Haddad left" })).toBeInTheDocument();
    const note = screen.getByRole("textbox", { name: "Note" });
    expect(note).toHaveValue("Moving to Riyadh");
    await userEvent.clear(note);
    await userEvent.type(note, "Back to Dubai next year");
    await userEvent.click(screen.getByRole("button", { name: "Done" }));

    expect(candidatesApi.changeExitReason).toHaveBeenCalledWith("p1", "c1", {
      reasonId: "other",
      note: "Back to Dubai next year",
    });
  });

  it("asks for the note of a held reason that is retired, as the server will", async () => {
    vi.mocked(pipelineApi.exitReasons).mockResolvedValue({ reasons: [reason("timing", "Timing")] });
    renderLine(given({ retired: true }));

    await userEvent.click(await screen.findByRole("button", { name: "Edit note" }));
    expect(await screen.findByRole("dialog", { name: "Change why Layla Haddad left" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Note" })).toBeInTheDocument();
  });
});
