import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aNextAction, aPipelinePage, aPipelineRow } from "../../../test/fixtures/pipelineRows";
import type { WorkspaceRole } from "../../auth/api/types";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import * as catalogApi from "../../workspace/api/pipelineApi";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineRow } from "../api/types";
import { PipelineView } from "./PipelineView";

const viewer: { id: string; fullName: string; avatarUrl: null; workspace: { roles: WorkspaceRole[] } } = {
  id: "u1",
  fullName: "Yara Haddad",
  avatarUrl: null,
  workspace: { roles: ["ADMIN"] },
};
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: viewer }) }));
vi.mock("../api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/pipelineApi")>()),
  getPipeline: vi.fn(),
  movePipelineRank: vi.fn(),
  setClientVisibility: vi.fn(),
}));
vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
}));
vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getTaskPresets: vi.fn(),
  getCandidateTasks: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../candidates/components/CandidateDrawerById", () => ({
  CandidateDrawerById: ({ candidateId, onClose }: { candidateId: string | null; onClose: () => void }) =>
    candidateId ? (
      <div role="dialog" aria-label={`Executive ${candidateId}`}>
        <button type="button" onClick={onClose}>
          Close drawer
        </button>
      </div>
    ) : null,
}));
vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/candidatesApi")>()),
  changeCandidateStatus: vi.fn(),
}));

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy Group",
  clientLogoUrl: null,
  positionTitle: "Chief Financial Officer",
  stage: "BRIEF",
  health: "OK",
  targetDate: null,
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team: [],
  representatives: [],
  companies: 0,
  candidates: 0,
  mappedCandidates: 0,
  engagedCandidates: 0,
  mappedCompanies: 0,
  createdAt: "2026-07-01T00:00:00Z",
  archivedAt: null,
  archivedByName: null,
};

const daysAgo = (days: number) => {
  const moment = new Date();
  moment.setDate(moment.getDate() - days);
  return moment.toISOString();
};

const laterToday = () => {
  const moment = new Date();
  moment.setHours(23, 59, 0, 0);
  return moment.toISOString();
};

const sara = { userId: "u3", fullName: "Sara Al-Mansour", avatarUrl: null };

const rows: PipelineRow[] = [
  aPipelineRow({ candidateId: "a", status: "identified", fullName: "Amal Haddad" }),
  aPipelineRow({ candidateId: "b", status: "researching", fullName: "Bilal Saeed" }),
  aPipelineRow({
    candidateId: "c",
    status: "engaged",
    fullName: "Carla Mendes",
    nextAction: aNextAction({
      taskId: "t-carla",
      text: "Send follow-up note",
      dueAt: daysAgo(3),
      assignee: sara,
      openCount: 3,
    }),
  }),
  aPipelineRow({
    candidateId: "d",
    status: "qualified",
    fullName: "Dina Farouk",
    nextAction: aNextAction({ taskId: "t-dina", text: "Share candidate report", dueAt: laterToday() }),
  }),
  aPipelineRow({ candidateId: "e", status: "offLimits", fullName: "Elias Nader", clientVisible: false }),
  aPipelineRow({ candidateId: "f", status: "notInterested", fullName: "Farah Aziz" }),
  aPipelineRow({ candidateId: "g", status: "offLimits", fullName: "Ghada Omar" }),
];

function LocationProbe() {
  return <output aria-label="Location">{useLocation().search}</output>;
}

function View({ search }: { search: string }) {
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set());
  const [inactiveOpen, setInactiveOpen] = useState(false);
  return (
    <PipelineView
      project={project}
      search={search}
      selectedIds={selectedIds}
      onToggleSelected={(candidateId) =>
        setSelectedIds((current) => {
          const next = new Set(current);
          if (next.has(candidateId)) next.delete(candidateId);
          else next.add(candidateId);
          return next;
        })
      }
      inactiveOpen={inactiveOpen}
      onInactiveOpenChange={setInactiveOpen}
    />
  );
}

function renderView({ address = "?view=pipeline", search = "" }: { address?: string; search?: string } = {}) {
  return render(
    <MemoryRouter initialEntries={[`/projects/p1/companies/universe${address}`]}>
      <LocationProbe />
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <View search={search} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const card = (name: string) => screen.getByRole("article", { name });

function dropOn(name: string, column: HTMLElement) {
  fireEvent.dragStart(card(name));
  fireEvent.dragOver(column);
  fireEvent.drop(column);
}

describe("PipelineView", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    viewer.workspace.roles = ["ADMIN"];
    vi.mocked(catalogApi.pipelineCatalog).mockResolvedValue(aSeededCatalog());
    vi.mocked(projectsApi.projects).mockResolvedValue([
      {
        ...project,
        team: [
          { memberId: "m1", userId: "u1", fullName: "Yara Haddad", avatarUrl: null, workspaceRoles: ["ADMIN"], projectRoles: ["LEAD"] },
          { memberId: "m3", userId: "u3", fullName: "Sara Al-Mansour", avatarUrl: null, workspaceRoles: ["MEMBER"], projectRoles: ["RESEARCHER"] },
        ],
      },
    ]);
    vi.mocked(tasksApi.getTaskPresets).mockResolvedValue([]);
    vi.mocked(pipelineApi.getPipeline).mockResolvedValue(aPipelinePage(rows));
  });

  it("reads the position's pipeline narrowed by the toolbar's search", async () => {
    renderView({ search: "carla" });

    await waitFor(() =>
      expect(pipelineApi.getPipeline).toHaveBeenCalledWith("p1", { q: "carla", pipelineStage: "" }, expect.anything()),
    );
  });

  it("says when the search matches nobody", async () => {
    vi.mocked(pipelineApi.getPipeline).mockResolvedValue(aPipelinePage([]));
    renderView({ search: "nobody" });

    expect(await screen.findByText("No executives match that search.")).toBeInTheDocument();
  });

  it("says when the position has no executives yet", async () => {
    vi.mocked(pipelineApi.getPipeline).mockResolvedValue(aPipelinePage([]));
    renderView();

    expect(
      await screen.findByText("No executives on this position yet. Add one, or find executives from Table."),
    ).toBeInTheDocument();
  });

  it("draws the card's next action, its +N and its assignee, and offers one where there is none", async () => {
    renderView();

    const carla = within(await screen.findByRole("article", { name: "Carla Mendes" }));
    expect(carla.getByText("Send follow-up note")).toBeInTheDocument();
    expect(carla.getByText("+2")).toBeInTheDocument();
    expect(carla.getByTitle("Assigned to Sara Al-Mansour")).toBeInTheDocument();
    expect(within(card("Amal Haddad")).getByText("No next action · Set one")).toBeInTheDocument();
  });

  it("sets a next action in the modal, with me first and chosen, and posts it as a task", async () => {
    vi.mocked(tasksApi.createTask).mockResolvedValue({} as never);
    renderView();
    await screen.findByRole("article", { name: "Amal Haddad" });

    await userEvent.click(within(card("Amal Haddad")).getByRole("button", { name: "No next action · Set one" }));
    const field = await screen.findByRole("combobox", { name: "What needs doing" });
    const dialog = within(screen.getByRole("dialog", { name: "Set next action" }));
    await userEvent.type(field, "Book introductory call");
    expect(dialog.getByRole("button", { name: /^Assign to/ })).toHaveAccessibleName("Assign to Me · Yara Haddad");
    await userEvent.click(dialog.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith(
        "p1",
        "a",
        expect.objectContaining({ title: "Book introductory call", kind: "call", assigneeUserId: "u1", presetId: null }),
      ),
    );
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Set next action" })).not.toBeInTheDocument());
  });

  it("opens the earliest task to edit from the card", async () => {
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({
      open: [
        {
          id: "t-carla",
          projectId: "p1",
          projectTitle: "Chief Financial Officer",
          candidateId: "c",
          personId: "person-c",
          personName: "Carla Mendes",
          title: "Send follow-up note",
          kind: "email",
          detail: null,
          presetId: null,
          note: null,
          assignee: sara,
          createdBy: sara,
          dueAt: daysAgo(3),
          status: "open",
          source: "manual",
          rule: null,
          completedAt: null,
          completedBy: null,
          createdAt: daysAgo(5),
          editable: true,
        },
      ],
      done: [],
      doneCount: 0,
    });
    renderView();

    await userEvent.click(
      within(await screen.findByRole("article", { name: "Carla Mendes" })).getByRole("button", {
        name: /Send follow-up note/,
      }),
    );

    expect(await screen.findByRole("combobox", { name: "What needs doing" })).toHaveValue("Send follow-up note");
    expect(screen.getByRole("dialog", { name: "Edit next action" })).toBeInTheDocument();
    expect(tasksApi.getCandidateTasks).toHaveBeenCalledWith("p1", "c", expect.anything());
    expect(screen.queryByRole("dialog", { name: /^Executive/ })).not.toBeInTheDocument();
  });

  it("opens the executive the address names, and drops them from it on close", async () => {
    renderView({ address: "?view=pipeline&candidate=c" });

    expect(await screen.findByRole("dialog", { name: "Executive c" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Close drawer" }));

    expect(screen.queryByRole("dialog", { name: "Executive c" })).not.toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent(/^\?view=pipeline$/);
  });

  it("writes the executive it opens into the address", async () => {
    renderView();

    await userEvent.click(await screen.findByRole("article", { name: "Amal Haddad" }));

    expect(await screen.findByRole("dialog", { name: "Executive a" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent("?view=pipeline&candidate=a");
  });

  it("flips client visibility at once and puts it back when the server refuses", async () => {
    let refuse: (error: unknown) => void = () => {};
    vi.mocked(pipelineApi.setClientVisibility).mockReturnValue(
      new Promise((_resolve, reject) => {
        refuse = reject;
      }),
    );
    renderView();
    await screen.findByRole("article", { name: "Amal Haddad" });
    const toggle = within(card("Amal Haddad")).getByRole("switch", { name: "Visible to client: Amal Haddad" });
    expect(toggle).toHaveAttribute("aria-checked", "true");

    await userEvent.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute("aria-checked", "false"));
    expect(pipelineApi.setClientVisibility).toHaveBeenCalledWith("p1", "a", false);
    expect(screen.queryByRole("dialog", { name: /^Executive/ })).not.toBeInTheDocument();

    refuse(new ApiRequestError({ code: "FORBIDDEN", detail: "no", status: 403, correlationId: "c1" }));
    await waitFor(() =>
      expect(within(card("Amal Haddad")).getByRole("switch", { name: /Amal Haddad/ })).toHaveAttribute(
        "aria-checked",
        "true",
      ),
    );
  });

  it("gives a card dropped on a stage's column that stage's default status, at the end of that column", async () => {
    vi.mocked(candidatesApi.changeCandidateStatus).mockResolvedValue({
      fullName: "Amal Haddad",
      status: "screened",
      statusLabel: "Screened",
    } as Candidate);
    renderView();
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    dropOn("Amal Haddad", qualified);

    await waitFor(() => expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "a", "screened"));
    await waitFor(() =>
      expect(pipelineApi.movePipelineRank).toHaveBeenCalledWith("p1", "a", {
        afterCandidateId: "d",
        beforeCandidateId: null,
      }),
    );
    expect(await screen.findByText("Amal Haddad moved to Qualified · Screened")).toBeInTheDocument();
  });

  it("shows a dropped card in its new column before the server answers, and puts it back if refused", async () => {
    let refuse: (error: unknown) => void = () => {};
    vi.mocked(candidatesApi.changeCandidateStatus).mockReturnValue(
      new Promise((_resolve, reject) => {
        refuse = reject;
      }),
    );
    renderView();
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    dropOn("Amal Haddad", qualified);

    expect(within(qualified).getByRole("article", { name: "Amal Haddad" })).toBeInTheDocument();
    expect(
      within(screen.getByRole("region", { name: "Identified" })).queryByRole("article", { name: "Amal Haddad" }),
    ).not.toBeInTheDocument();

    refuse(new ApiRequestError({ code: "FORBIDDEN", detail: "no", status: 403, correlationId: "c1" }));
    expect(
      await within(screen.getByRole("region", { name: "Identified" })).findByRole("article", { name: "Amal Haddad" }),
    ).toBeInTheDocument();
    expect(pipelineApi.movePipelineRank).not.toHaveBeenCalled();
  });

  it("says only that the order was refused when the status saved but the rank did not", async () => {
    vi.mocked(candidatesApi.changeCandidateStatus).mockResolvedValue({
      fullName: "Amal Haddad",
      status: "screened",
      statusLabel: "Screened",
    } as Candidate);
    vi.mocked(pipelineApi.movePipelineRank).mockRejectedValue(
      new ApiRequestError({ code: "PIPELINE_ORDER_INVALID", detail: "moved", status: 409, correlationId: "c1" }),
    );
    renderView();
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    dropOn("Amal Haddad", qualified);

    expect(await screen.findByText("The pipeline changed meanwhile. Reload and try again.")).toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "a", "screened");
    expect(screen.queryByText("Amal Haddad moved to Qualified · Screened")).not.toBeInTheDocument();
  });

  it("reorders a card within its own column without changing its status", async () => {
    vi.mocked(pipelineApi.movePipelineRank).mockReturnValue(new Promise(() => {}));
    renderView();
    const identified = await screen.findByRole("region", { name: "Identified" });

    dropOn("Amal Haddad", identified);

    await waitFor(() =>
      expect(pipelineApi.movePipelineRank).toHaveBeenCalledWith("p1", "a", {
        afterCandidateId: "b",
        beforeCandidateId: null,
      }),
    );
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
    expect(within(identified).getAllByRole("article").map((article) => article.getAttribute("aria-label"))).toEqual([
      "Bilal Saeed",
      "Amal Haddad",
    ]);
  });

  it("ticks a card without opening it", async () => {
    renderView();
    const amal = await screen.findByRole("article", { name: "Amal Haddad" });

    await userEvent.click(within(amal).getByRole("checkbox", { name: "Select Amal Haddad" }));

    expect(within(amal).getByRole("checkbox", { name: "Select Amal Haddad" })).toBeChecked();
    expect(within(card("Bilal Saeed")).getByRole("checkbox", { name: "Select Bilal Saeed" })).not.toBeChecked();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("folds the Inactive column, and unfolded groups it by status, only the statuses someone holds", async () => {
    renderView();
    const column = await screen.findByRole("region", { name: "Inactive" });
    const inactive = within(column);
    const fold = inactive.getByRole("button", { name: /^Inactive/ });

    expect(fold).toHaveAttribute("aria-expanded", "false");
    expect(inactive.queryByRole("group")).not.toBeInTheDocument();

    await userEvent.click(fold);

    expect(fold).toHaveAttribute("aria-expanded", "true");
    const offLimits = within(inactive.getByRole("group", { name: "Off-limits" }));
    expect(offLimits.getByText("Elias Nader")).toBeInTheDocument();
    expect(offLimits.getByText("Ghada Omar")).toBeInTheDocument();
    expect(within(inactive.getByRole("group", { name: "Not interested" })).getByText("Farah Aziz")).toBeInTheDocument();
    expect(inactive.queryByRole("group", { name: "Withdrew" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Declined" })).not.toBeInTheDocument();

    await userEvent.click(fold);

    expect(fold).toHaveAttribute("aria-expanded", "false");
    expect(inactive.queryByRole("group", { name: "Off-limits" })).not.toBeInTheDocument();
  });
});
