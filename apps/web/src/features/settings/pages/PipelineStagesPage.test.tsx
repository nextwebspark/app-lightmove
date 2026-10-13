import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineCatalog } from "../../workspace/api/types";
import { PipelineStagesPage } from "./PipelineStagesPage";

vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
  createStage: vi.fn(),
  updateStage: vi.fn(),
  reorderStages: vi.fn(),
  createStatus: vi.fn(),
  updateStatus: vi.fn(),
  reorderStatuses: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <PipelineStagesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const card = (label: string) => screen.getByRole("region", { name: label });

function withStage(catalog: PipelineCatalog, label: string): PipelineCatalog {
  const added = {
    id: "stage-assessed",
    key: "assessed",
    label,
    colour: "neutral",
    kind: "active" as const,
    position: 6,
    system: false,
    retired: false,
    statuses: [],
  };
  return { stages: [...catalog.stages.slice(0, -1), added, catalog.stages[catalog.stages.length - 1]] };
}

describe("PipelineStagesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(aSeededCatalog());
  });

  it("lists every stage in order with its kind, its statuses, the default and the built-in ones", async () => {
    renderPage();
    await screen.findByRole("region", { name: "Identified" });

    expect(screen.getAllByRole("region").map((region) => region.getAttribute("aria-label"))).toEqual([
      "Identified",
      "Contacted",
      "Interested",
      "Qualified",
      "Shortlisted",
      "Declined",
    ]);
    expect(within(card("Contacted")).getByText("Active")).toBeInTheDocument();
    expect(within(card("Contacted")).getByText("4 statuses")).toBeInTheDocument();
    expect(within(card("Declined")).getByText("Closed")).toBeInTheDocument();

    const contacted = within(card("Contacted")).getByRole("listitem", { name: "Contacted" });
    expect(within(contacted).getByText("Default")).toBeInTheDocument();
    expect(within(contacted).getByText("System")).toBeInTheDocument();
    expect(within(contacted).queryByRole("button", { name: "Retire Contacted" })).not.toBeInTheDocument();
    const followUp = within(card("Contacted")).getByRole("listitem", { name: "Follow-up sent" });
    expect(within(followUp).queryByText("System")).not.toBeInTheDocument();
    expect(within(followUp).getByRole("button", { name: "Make default" })).toBeInTheDocument();
  });

  it("adds a stage and draws the catalog the server answers", async () => {
    vi.mocked(pipelineApi.createStage).mockResolvedValue(withStage(aSeededCatalog(), "Assessed"));
    renderPage();
    await screen.findByRole("region", { name: "Identified" });

    await userEvent.type(screen.getByLabelText("New stage"), "Assessed");
    await userEvent.click(screen.getByRole("button", { name: "Add stage" }));

    await waitFor(() => expect(pipelineApi.createStage).toHaveBeenCalledWith("Assessed"));
    expect(await screen.findByRole("region", { name: "Assessed" })).toBeInTheDocument();
    expect(screen.getByText("Stage added to every position")).toBeInTheDocument();
  });

  it("adds a status to a stage, and renames, recolours, makes default and retires one", async () => {
    const catalog = aSeededCatalog();
    vi.mocked(pipelineApi.createStatus).mockResolvedValue(catalog);
    vi.mocked(pipelineApi.updateStatus).mockResolvedValue(catalog);
    renderPage();
    await screen.findByRole("region", { name: "Interested" });

    await userEvent.type(within(card("Interested")).getByLabelText("New status in Interested"), "Second look");
    await userEvent.click(within(card("Interested")).getByRole("button", { name: "Add status" }));
    await waitFor(() => expect(pipelineApi.createStatus).toHaveBeenCalledWith("stage-interested", "Second look"));

    const considering = within(card("Interested")).getByRole("listitem", { name: "Considering" });
    await userEvent.click(within(considering).getByRole("button", { name: "Rename" }));
    await userEvent.clear(within(considering).getByLabelText("Status name"));
    await userEvent.type(within(considering).getByLabelText("Status name"), "Weighing it up");
    await userEvent.click(within(considering).getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(pipelineApi.updateStatus).toHaveBeenCalledWith("status-considering", { label: "Weighing it up" }),
    );
    expect(await screen.findByText("Renamed on every position")).toBeInTheDocument();

    await userEvent.click(within(considering).getByRole("button", { name: "Colour of Considering" }));
    await waitFor(() => expect(pipelineApi.updateStatus).toHaveBeenCalledWith("status-considering", { colour: "accent" }));

    await userEvent.click(within(considering).getByRole("button", { name: "Make default" }));
    await waitFor(() => expect(pipelineApi.updateStatus).toHaveBeenCalledWith("status-considering", { isDefault: true }));

    await userEvent.click(within(considering).getByRole("button", { name: "Retire Considering" }));
    await waitFor(() => expect(pipelineApi.updateStatus).toHaveBeenCalledWith("status-considering", { retired: true }));
  });

  it("opens a name for typing on Rename without turning the pressed button into Save", async () => {
    vi.mocked(pipelineApi.updateStatus).mockResolvedValue(aSeededCatalog());
    vi.mocked(pipelineApi.updateStage).mockResolvedValue(aSeededCatalog());
    renderPage();
    await screen.findByRole("region", { name: "Interested" });

    const considering = within(card("Interested")).getByRole("listitem", { name: "Considering" });
    const statusRename = within(considering).getByRole("button", { name: "Rename" });
    await userEvent.click(statusRename);
    expect(statusRename).not.toBeInTheDocument();
    expect(within(considering).getByLabelText("Status name")).toHaveValue("Considering");
    expect(pipelineApi.updateStatus).not.toHaveBeenCalled();

    const stageRename = within(card("Interested"))
      .getAllByRole("button", { name: "Rename" })
      .find((button) => !button.closest("li"))!;
    await userEvent.click(stageRename);
    expect(stageRename).not.toBeInTheDocument();
    expect(within(card("Interested")).getByLabelText("Stage name")).toHaveValue("Interested");
    expect(pipelineApi.updateStage).not.toHaveBeenCalled();
    expect(screen.queryByText("Renamed on every position")).not.toBeInTheDocument();
  });

  it("reorders stages by drag or by the handle's arrow keys, keeping the closed stage last", async () => {
    const catalog = aSeededCatalog();
    vi.mocked(pipelineApi.reorderStages).mockImplementation(async (ids) => ({
      stages: ids.map((id) => catalog.stages.find((stage) => stage.id === id)!),
    }));
    renderPage();
    await screen.findByRole("region", { name: "Identified" });

    fireEvent.keyDown(screen.getByRole("button", { name: "Reorder Identified stage" }), { key: "ArrowDown" });
    await waitFor(() =>
      expect(pipelineApi.reorderStages).toHaveBeenLastCalledWith([
        "stage-contacted",
        "stage-identified",
        "stage-interested",
        "stage-qualified",
        "stage-shortlisted",
        "stage-declined",
      ]),
    );
    await waitFor(() =>
      expect(screen.getAllByRole("region")[0]).toHaveAttribute("aria-label", "Contacted"),
    );

    fireEvent.dragStart(card("Shortlisted"));
    fireEvent.dragOver(card("Contacted"));
    fireEvent.drop(card("Contacted"));
    await waitFor(() =>
      expect(pipelineApi.reorderStages).toHaveBeenLastCalledWith([
        "stage-shortlisted",
        "stage-contacted",
        "stage-identified",
        "stage-interested",
        "stage-qualified",
        "stage-declined",
      ]),
    );
    expect(screen.queryByRole("button", { name: "Reorder Declined stage" })).not.toBeInTheDocument();
  });

  it("reorders statuses within their stage", async () => {
    vi.mocked(pipelineApi.reorderStatuses).mockResolvedValue(aSeededCatalog());
    renderPage();
    await screen.findByRole("region", { name: "Contacted" });
    const contacted = card("Contacted");

    fireEvent.dragStart(within(contacted).getByRole("listitem", { name: "Call booked" }));
    fireEvent.dragOver(within(contacted).getByRole("listitem", { name: "Contacted" }));
    fireEvent.drop(within(contacted).getByRole("listitem", { name: "Contacted" }));

    await waitFor(() =>
      expect(pipelineApi.reorderStatuses).toHaveBeenCalledWith("stage-contacted", [
        "status-callBooked",
        "status-contacted",
        "status-followUpSent",
        "status-engaged",
      ]),
    );
  });

  it("puts a reorder back when the server refuses it", async () => {
    vi.mocked(pipelineApi.reorderStages).mockRejectedValue(
      new ApiRequestError({ code: "PIPELINE_ORDER_INVALID", detail: "x", status: 409, correlationId: "c1" }),
    );
    renderPage();
    await screen.findByRole("region", { name: "Identified" });

    fireEvent.keyDown(screen.getByRole("button", { name: "Reorder Identified stage" }), { key: "ArrowDown" });

    expect(await screen.findByText("The pipeline changed meanwhile. Reload and try again.")).toBeInTheDocument();
    expect(screen.getAllByRole("region")[0]).toHaveAttribute("aria-label", "Identified");
  });

  it("refuses to retire a stage that still has live statuses, and retires an empty one", async () => {
    const catalog = withStage(aSeededCatalog(), "Assessed");
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(catalog);
    vi.mocked(pipelineApi.updateStage).mockResolvedValue(aSeededCatalog());
    renderPage();
    await screen.findByRole("region", { name: "Assessed" });

    await userEvent.click(within(card("Qualified")).getByRole("button", { name: "Retire Qualified stage" }));
    const dialog = await screen.findByRole("dialog", { name: "Retire Qualified" });
    expect(within(dialog).getByText(/Move or retire this stage's statuses first/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "OK" }));
    expect(pipelineApi.updateStage).not.toHaveBeenCalled();

    await userEvent.click(within(card("Assessed")).getByRole("button", { name: "Retire Assessed stage" }));
    await waitFor(() => expect(pipelineApi.updateStage).toHaveBeenCalledWith("stage-assessed", { retired: true }));
    expect(await screen.findByText("Assessed retired")).toBeInTheDocument();
    expect(within(card("Declined")).queryByRole("button", { name: "Retire Declined stage" })).not.toBeInTheDocument();
  });

  it("says why the server refused a change", async () => {
    vi.mocked(pipelineApi.createStatus).mockRejectedValue(
      new ApiRequestError({ code: "PIPELINE_LABEL_TAKEN", detail: "taken", status: 409, correlationId: "c1" }),
    );
    vi.mocked(pipelineApi.updateStatus).mockRejectedValue(
      new ApiRequestError({ code: "PIPELINE_STATUS_SYSTEM", detail: "system", status: 409, correlationId: "c2" }),
    );
    renderPage();
    await screen.findByRole("region", { name: "Interested" });

    await userEvent.type(within(card("Interested")).getByLabelText("New status in Interested"), "considering");
    await userEvent.click(within(card("Interested")).getByRole("button", { name: "Add status" }));
    expect(await screen.findByText("That name is already in use.")).toBeInTheDocument();

    const engaged = within(card("Contacted")).getByRole("listitem", { name: "Engaged" });
    await userEvent.click(within(engaged).getByRole("button", { name: "Make default" }));
    expect(
      await screen.findByText("Built-in statuses can be renamed and recoloured, but not retired or moved."),
    ).toBeInTheDocument();
  });

  it("is read-only for a member who is not an admin", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    renderPage();
    await screen.findByRole("region", { name: "Identified" });

    expect(screen.getByText("Only workspace admins can change stages and statuses.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Rename" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add status" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("New stage")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Reorder / })).not.toBeInTheDocument();
    expect(within(card("Identified")).getByRole("button", { name: "Colour of Identified stage" })).toBeDisabled();
    expect(card("Identified")).not.toHaveAttribute("draggable");
  });
});
