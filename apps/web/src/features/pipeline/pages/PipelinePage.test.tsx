import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
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
import PipelinePage from "./PipelinePage";

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

function renderPage(search = "?view=list") {
  return render(
    <MemoryRouter initialEntries={[`/projects/p1/pipeline${search}`]}>
      <LocationProbe />
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/projects/:projectId/pipeline" element={<PipelinePage />} />
              <Route path="/projects/:projectId/companies/universe" element={<p>In universe</p>} />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const row = (name: string) => screen.getByRole("row", { name });

describe("PipelinePage", () => {
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
    vi.mocked(pipelineApi.getPipeline).mockImplementation(async (_projectId, query) => ({
      ...aPipelinePage(query.pipelineStage ? rows.filter((r) => r.pipelineStage.key === query.pipelineStage) : rows),
      stageCounts: aPipelinePage(rows).stageCounts,
    }));
  });

  it("opens as the board, and as the list once asked", async () => {
    renderPage("");

    expect(await screen.findByRole("radio", { name: "Board" })).toBeChecked();
    expect(await screen.findByRole("region", { name: "Qualified" })).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Stages" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "List" }));

    expect(await screen.findByRole("row", { name: "Amal Haddad" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent("?view=list");
  });

  it("counts each stage on its pill, the closed stage as Inactive, and everyone on All", async () => {
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });
    const pills = within(screen.getByRole("group", { name: "Stages" }));

    expect(pills.getByRole("button", { name: /^All\s*7(?!\d)/ })).toHaveAttribute("aria-pressed", "true");
    expect(pills.getByRole("button", { name: /^Identified\s*2(?!\d)/ })).toBeInTheDocument();
    expect(pills.getByRole("button", { name: /^Contacted\s*1(?!\d)/ })).toBeInTheDocument();
    expect(pills.getByRole("button", { name: /^Interested\s*0(?!\d)/ })).toBeInTheDocument();
    expect(pills.getByRole("button", { name: /^Inactive\s*3(?!\d)/ })).toBeInTheDocument();
  });

  it("narrows to a stage, keeps it in the address, and shows everyone again on All", async () => {
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });
    const pills = within(screen.getByRole("group", { name: "Stages" }));

    await userEvent.click(pills.getByRole("button", { name: /^Qualified\s*1(?!\d)/ }));
    await waitFor(() =>
      expect(pipelineApi.getPipeline).toHaveBeenLastCalledWith("p1", { q: "", pipelineStage: "qualified" }, expect.anything()),
    );
    await waitFor(() => expect(screen.queryByRole("row", { name: "Amal Haddad" })).not.toBeInTheDocument());
    expect(pills.getByRole("button", { name: /^Qualified\s*1(?!\d)/ })).toHaveAttribute("aria-pressed", "true");
    expect(pills.getByRole("button", { name: /^All\s*7(?!\d)/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent("?view=list&stage=qualified");

    await userEvent.click(pills.getByRole("button", { name: /^All\s*7(?!\d)/ }));
    await screen.findByRole("row", { name: "Amal Haddad" });
    expect(pipelineApi.getPipeline).toHaveBeenLastCalledWith("p1", { q: "", pipelineStage: "" }, expect.anything());
    expect(pills.getByRole("button", { name: /^All\s*7(?!\d)/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent(/^\?view=list$/);
  });

  it("clears a stage when its pill is pressed again", async () => {
    renderPage("?stage=qualified");
    const pills = within(await screen.findByRole("group", { name: "Stages" }));

    await userEvent.click(pills.getByRole("button", { name: /^Qualified\s*1(?!\d)/ }));
    await screen.findByRole("row", { name: "Amal Haddad" });
    expect(pills.getByRole("button", { name: /^All\s*7(?!\d)/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("opens on the stage the address names", async () => {
    renderPage("?stage=qualified");

    await waitFor(() =>
      expect(pipelineApi.getPipeline).toHaveBeenCalledWith("p1", { q: "", pipelineStage: "qualified" }, expect.anything()),
    );
    expect(await screen.findByRole("row", { name: "Dina Farouk" })).toBeInTheDocument();
    expect(screen.queryByRole("row", { name: "Amal Haddad" })).not.toBeInTheDocument();
  });

  it("shows everyone when the address names a stage the workspace does not have", async () => {
    renderPage("?stage=nonsense");

    expect(await screen.findByRole("row", { name: "Amal Haddad" })).toBeInTheDocument();
    await waitFor(() =>
      expect(pipelineApi.getPipeline).toHaveBeenLastCalledWith("p1", { q: "", pipelineStage: "" }, expect.anything()),
    );
    const pills = within(screen.getByRole("group", { name: "Stages" }));
    expect(pills.getByRole("button", { name: /^All\s*7(?!\d)/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("draws each row's stage with its status beneath, and its next action with its due, assignee and how many more", async () => {
    renderPage();
    await screen.findByRole("row", { name: "Carla Mendes" });

    expect(within(row("Bilal Saeed")).getByText("Identified")).toBeInTheDocument();
    expect(within(row("Bilal Saeed")).getByText("Researching")).toBeInTheDocument();
    const carla = within(row("Carla Mendes"));
    expect(carla.getByText("Send follow-up note")).toBeInTheDocument();
    const overdue = carla.getByText(/^Overdue · \d{2} [A-Z][a-z]{2} \d{4}$/);
    expect(overdue).toHaveAttribute("data-tone", "overdue");
    expect(overdue).toHaveClass("text-u-offlimits");
    expect(carla.getByText("· +2 more")).toBeInTheDocument();
    expect(carla.getByTitle("Assigned to Sara Al-Mansour")).toBeInTheDocument();
    const today = within(row("Dina Farouk")).getByText("Today · 23:59");
    expect(today).toHaveAttribute("data-tone", "today");
    expect(today).toHaveClass("text-u-signal");
    expect(within(row("Dina Farouk")).queryByText(/more/)).not.toBeInTheDocument();
    expect(within(row("Amal Haddad")).getByRole("button", { name: "Set next action" })).toBeInTheDocument();
  });

  it("draws the board card's next action, its +N and its assignee, and offers one where there is none", async () => {
    renderPage("");

    const carla = within(await screen.findByRole("article", { name: "Carla Mendes" }));
    expect(carla.getByText("Send follow-up note")).toBeInTheDocument();
    expect(carla.getByText("+2")).toBeInTheDocument();
    expect(carla.getByTitle("Assigned to Sara Al-Mansour")).toBeInTheDocument();
    expect(
      within(screen.getByRole("article", { name: "Amal Haddad" })).getByText("No next action · Set one"),
    ).toBeInTheDocument();
  });

  it("sets a next action in the modal, with me first and chosen, and posts it as a task", async () => {
    vi.mocked(tasksApi.createTask).mockResolvedValue({} as never);
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });

    await userEvent.click(within(row("Amal Haddad")).getByRole("button", { name: "Set next action" }));
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

  it("opens the earliest task to edit from the board card", async () => {
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
          noteId: null,
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
    renderPage("");

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
    renderPage("?view=list&candidate=c");

    expect(await screen.findByRole("dialog", { name: "Executive c" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Close drawer" }));

    expect(screen.queryByRole("dialog", { name: "Executive c" })).not.toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent(/^\?view=list$/);
  });

  it("writes the executive it opens into the address", async () => {
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });

    await userEvent.click(within(row("Amal Haddad")).getByRole("button", { name: "Amal Haddad" }));

    expect(await screen.findByRole("dialog", { name: "Executive a" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent("?view=list&candidate=a");
  });

  it("flips client visibility at once and puts it back when the server refuses", async () => {
    let refuse: (error: unknown) => void = () => {};
    vi.mocked(pipelineApi.setClientVisibility).mockReturnValue(
      new Promise((_resolve, reject) => {
        refuse = reject;
      }),
    );
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });
    const toggle = within(row("Amal Haddad")).getByRole("switch", { name: "Visible to client: Amal Haddad" });
    expect(toggle).toHaveAttribute("aria-checked", "true");

    await userEvent.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute("aria-checked", "false"));
    expect(pipelineApi.setClientVisibility).toHaveBeenCalledWith("p1", "a", false);

    refuse(new ApiRequestError({ code: "FORBIDDEN", detail: "no", status: 403, correlationId: "c1" }));
    await waitFor(() =>
      expect(within(row("Amal Haddad")).getByRole("switch", { name: /Amal Haddad/ })).toHaveAttribute(
        "aria-checked",
        "true",
      ),
    );
  });

  it("reorders a row within its stage at once, sending its new neighbours", async () => {
    vi.mocked(pipelineApi.movePipelineRank).mockReturnValue(new Promise(() => {}));
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });

    fireEvent.keyDown(screen.getByRole("button", { name: "Reorder Amal Haddad" }), { key: "ArrowDown" });

    await waitFor(() =>
      expect(pipelineApi.movePipelineRank).toHaveBeenCalledWith("p1", "a", {
        afterCandidateId: "b",
        beforeCandidateId: null,
      }),
    );
    await waitFor(() =>
      expect(screen.getAllByRole("row").map((r) => r.getAttribute("aria-label")).slice(1, 3)).toEqual([
        "Bilal Saeed",
        "Amal Haddad",
      ]),
    );
  });

  it("gives a card dropped on a stage's column that stage's default status, at the end of that column", async () => {
    vi.mocked(candidatesApi.changeCandidateStatus).mockResolvedValue({
      fullName: "Amal Haddad",
      status: "screened",
      statusLabel: "Screened",
    } as Candidate);
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });
    await userEvent.click(screen.getByRole("radio", { name: "Board" }));
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    fireEvent.dragStart(screen.getByRole("article", { name: "Amal Haddad" }));
    fireEvent.dragOver(qualified);
    fireEvent.drop(qualified);

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
    renderPage("");
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    fireEvent.dragStart(screen.getByRole("article", { name: "Amal Haddad" }));
    fireEvent.dragOver(qualified);
    fireEvent.drop(qualified);

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
    renderPage("");
    const qualified = await screen.findByRole("region", { name: "Qualified" });

    fireEvent.dragStart(screen.getByRole("article", { name: "Amal Haddad" }));
    fireEvent.dragOver(qualified);
    fireEvent.drop(qualified);

    expect(await screen.findByText("The pipeline changed meanwhile. Reload and try again.")).toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "a", "screened");
    expect(screen.queryByText("Amal Haddad moved to Qualified · Screened")).not.toBeInTheDocument();
  });

  it("reorders a card within its own column without changing its status", async () => {
    vi.mocked(pipelineApi.movePipelineRank).mockReturnValue(new Promise(() => {}));
    renderPage("");
    const identified = await screen.findByRole("region", { name: "Identified" });

    fireEvent.dragStart(within(identified).getByRole("article", { name: "Amal Haddad" }));
    fireEvent.dragOver(identified);
    fireEvent.drop(identified);

    await waitFor(() =>
      expect(pipelineApi.movePipelineRank).toHaveBeenCalledWith("p1", "a", {
        afterCandidateId: "b",
        beforeCandidateId: null,
      }),
    );
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
    expect(within(identified).getAllByRole("article").map((card) => card.getAttribute("aria-label"))).toEqual([
      "Bilal Saeed",
      "Amal Haddad",
    ]);
  });

  it("ticks a card on the board and keeps the tick on the list", async () => {
    renderPage("");
    const card = await screen.findByRole("article", { name: "Amal Haddad" });

    await userEvent.click(within(card).getByRole("checkbox", { name: "Select Amal Haddad" }));

    expect(within(card).getByRole("checkbox", { name: "Select Amal Haddad" })).toBeChecked();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "List" }));

    const listed = await screen.findByRole("row", { name: "Amal Haddad" });
    expect(within(listed).getByRole("checkbox", { name: "Select Amal Haddad" })).toBeChecked();
    expect(within(row("Bilal Saeed")).getByRole("checkbox", { name: "Select Bilal Saeed" })).not.toBeChecked();
  });

  it("groups the Inactive panel by status, only the statuses someone holds", async () => {
    renderPage();
    await screen.findByRole("row", { name: "Amal Haddad" });
    await userEvent.click(screen.getByRole("radio", { name: "Board" }));
    const inactive = within(await screen.findByRole("region", { name: "Inactive" }));

    const offLimits = within(inactive.getByRole("group", { name: "Off-limits" }));
    expect(offLimits.getByText("Elias Nader")).toBeInTheDocument();
    expect(offLimits.getByText("Ghada Omar")).toBeInTheDocument();
    expect(within(inactive.getByRole("group", { name: "Not interested" })).getByText("Farah Aziz")).toBeInTheDocument();
    expect(inactive.queryByRole("group", { name: "Withdrew" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Declined" })).not.toBeInTheDocument();
  });

  it("sends someone without a staff seat to In universe", async () => {
    viewer.workspace.roles = ["MEMBER"];
    renderPage();

    expect(await screen.findByText("In universe")).toBeInTheDocument();
    expect(pipelineApi.getPipeline).not.toHaveBeenCalled();
  });
});
