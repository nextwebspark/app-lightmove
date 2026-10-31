import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aCurrentCatalog, aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aNextAction, aPipelinePage, aPipelineRow } from "../../../test/fixtures/pipelineRows";
import type { WorkspaceRole } from "../../auth/api/types";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as poolApi from "../../candidates/api/poolApi";
import type { Candidate, CandidatePhone, PersonRecord } from "../../candidates/api/types";
import * as mailboxApi from "../../outreach/api/mailboxApi";
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
vi.mock("../../candidates/api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/poolApi")>()),
  getPerson: vi.fn(),
}));
vi.mock("../../outreach/api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../outreach/api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));
vi.mock("../../outreach/components/NoteComposerModal", () => ({
  NoteComposerModal: ({ kind, fullName }: { kind: string | null; fullName: string }) =>
    kind ? <div role="dialog" aria-label={`${kind === "call" ? "Log a call with" : "Add a note on"} ${fullName}`} /> : null,
}));
vi.mock("../../outreach/components/EmailComposerDialog", () => ({
  EmailComposerDialog: ({ fullName }: { fullName: string }) => <div role="dialog" aria-label={`Email ${fullName}`} />,
}));
vi.mock("../../outreach/components/BookCallDialog", () => ({
  BookCallDialog: ({ fullName, candidateStatus }: { fullName: string; candidateStatus: string }) => (
    <div role="dialog" aria-label={`Book a call with ${fullName}`}>
      {candidateStatus}
    </div>
  ),
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

const connected: mailboxApi.ConnectedMailbox = {
  address: "yara@firm.example",
  provider: "google",
  status: "ACTIVE",
  dailyCap: 50,
  timeZone: "Asia/Dubai",
  connectedAt: "2026-09-01T00:00:00Z",
  movesOffNylas: false,
  runsStoppedByMove: 0,
  liveSequences: 0,
  livePeople: 0,
  readsReplies: false,
  reconnectToReadReplies: false,
  sentToday: 0,
  scheduledEmails: 0,
};

const aPhone = (number: string): CandidatePhone => ({
  number,
  kind: "work",
  verified: false,
  status: null,
  source: "manual",
  foundAt: "2026-09-01T00:00:00Z",
});

function aPersonRecord({
  phones = [],
  emails = [],
  doNotContact = false,
}: { phones?: string[]; emails?: string[]; doNotContact?: boolean } = {}): PersonRecord {
  return {
    contacts: {
      phones: phones.map(aPhone),
      emails: emails.map((address) => ({
        address,
        kind: "work",
        verified: false,
        status: null,
        source: "manual",
        foundAt: "2026-09-01T00:00:00Z",
      })),
      emailsLookedUpAt: null,
      phonesLookedUpAt: null,
      source: null,
    },
    doNotContact: doNotContact
      ? { reason: "Asked not to be approached.", setByUserId: "u1", setByName: "Yara", setAt: "2026-09-01T00:00:00Z" }
      : null,
  } as PersonRecord;
}

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
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      bookingLinkOffered: false,
      connection: connected,
    });
    vi.mocked(poolApi.getPerson).mockResolvedValue(aPersonRecord());
    localStorage.clear();
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

  it("draws the card's next action and when it falls due, and offers one where there is none", async () => {
    renderView();

    const carla = within(await screen.findByRole("article", { name: "Carla Mendes" }));
    expect(carla.getByText("Send follow-up note")).toBeInTheDocument();
    expect(carla.getByText(/^Overdue/)).toBeInTheDocument();
    expect(carla.queryByText("+2")).not.toBeInTheDocument();
    expect(carla.queryByTitle("Assigned to Sara Al-Mansour")).not.toBeInTheDocument();
    expect(within(card("Amal Haddad")).getByText("No next action")).toBeInTheDocument();
  });

  it("sets a next action in the modal, with me first and chosen, and posts it as a task", async () => {
    vi.mocked(tasksApi.createTask).mockResolvedValue({} as never);
    renderView();
    await screen.findByRole("article", { name: "Amal Haddad" });

    await userEvent.click(within(card("Amal Haddad")).getByRole("button", { name: "No next action" }));
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

  it("draws everyone out of process in the band, a stage of ways out alone in a cell of its own", async () => {
    renderView();
    await screen.findByRole("region", { name: "Identified" });

    const declined = within(screen.getByRole("group", { name: "Out of process · Declined" }));
    expect(declined.getByText("Declined")).toBeInTheDocument();
    expect(declined.getAllByRole("article").map((article) => article.getAttribute("aria-label"))).toEqual([
      "Elias Nader",
      "Farah Aziz",
      "Ghada Omar",
    ]);
    expect(declined.getAllByText(/^Off-limits ·/)).toHaveLength(2);
    expect(screen.queryByRole("region", { name: "Declined" })).not.toBeInTheDocument();
    expect(
      within(screen.getByRole("group", { name: "Out of process · Identified" })).queryByRole("article"),
    ).not.toBeInTheDocument();
  });

  it("refuses a card dropped in a cell whose stage has no way out", async () => {
    renderView();
    await screen.findByRole("region", { name: "Identified" });

    dropOn("Amal Haddad", screen.getByRole("group", { name: "Out of process · Identified" }));

    expect(screen.queryByRole("group", { name: "Mark Amal Haddad out of process" })).not.toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
  });

  it("forgets the band's height on a double-click of its grip", async () => {
    localStorage.setItem("lightmove.pipeline.outOfProcessHeight", "120");
    renderView();

    fireEvent.doubleClick(await screen.findByRole("separator", { name: "Resize Out of process" }));

    expect(localStorage.getItem("lightmove.pipeline.outOfProcessHeight")).toBeNull();
  });

  it("offers a card's four actions, none of which opens the card", async () => {
    renderView();
    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));

    for (const name of ["Log call", "Email", "Add note", "Schedule"]) {
      expect(amal.getByRole("button", { name })).toBeInTheDocument();
    }
    expect(amal.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();

    await userEvent.click(amal.getByRole("button", { name: "Add note" }));

    expect(screen.getByRole("dialog", { name: "Add a note on Amal Haddad" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: /^Executive/ })).not.toBeInTheDocument();
  });

  it("shows the numbers on file to call before logging the call", async () => {
    vi.mocked(poolApi.getPerson).mockResolvedValue(aPersonRecord({ phones: ["+971 50 123 4567"] }));
    renderView();

    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    await userEvent.click(amal.getByRole("button", { name: "Log call" }));
    const panel = within(await screen.findByRole("group", { name: "Call Amal Haddad" }));

    expect(await panel.findByRole("link", { name: "+971 50 123 4567" })).toHaveAttribute("href", "tel:+971501234567");
    expect(poolApi.getPerson).toHaveBeenCalledWith("person-a", expect.anything());
    await userEvent.click(panel.getByRole("button", { name: "Log call" }));

    expect(screen.getByRole("dialog", { name: "Log a call with Amal Haddad" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: /^Executive/ })).not.toBeInTheDocument();
  });

  it("says when there is no number on file and opens the drawer's Contact section", async () => {
    renderView();

    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    await userEvent.click(amal.getByRole("button", { name: "Log call" }));
    const panel = within(await screen.findByRole("group", { name: "Call Amal Haddad" }));
    await userEvent.click(await panel.findByRole("button", { name: "Open Contact" }));

    expect(await screen.findByRole("dialog", { name: "Executive a" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Location" })).toHaveTextContent("candidate=a&section=contact");
  });

  it("writes an email from the card", async () => {
    renderView();

    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    await userEvent.click(amal.getByRole("button", { name: "Email" }));

    expect(screen.getByRole("dialog", { name: "Email Amal Haddad" })).toBeInTheDocument();
  });

  it("books a call for someone with an email, carrying their status", async () => {
    vi.mocked(poolApi.getPerson).mockResolvedValue(aPersonRecord({ emails: ["amal@enbd.example"] }));
    renderView();
    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    await waitFor(() => expect(amal.getByRole("button", { name: "Schedule" })).not.toHaveAttribute("aria-disabled"));

    await userEvent.click(amal.getByRole("button", { name: "Schedule" }));

    expect(await screen.findByRole("dialog", { name: "Book a call with Amal Haddad" })).toHaveTextContent("identified");
  });

  it("says why a call cannot be booked for someone marked do not contact", async () => {
    vi.mocked(poolApi.getPerson).mockResolvedValue(
      aPersonRecord({ emails: ["amal@enbd.example"], doNotContact: true }),
    );
    renderView();
    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    await waitFor(() => expect(amal.getByRole("button", { name: "Schedule" })).not.toHaveAttribute("aria-disabled"));

    await userEvent.click(amal.getByRole("button", { name: "Schedule" }));

    expect(await screen.findByText("Amal Haddad is marked do not contact.")).toBeInTheDocument();
  });

  it("greys Schedule until a mailbox is connected, and says so", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      bookingLinkOffered: false,
      connection: null,
    });
    renderView();
    const amal = within(await screen.findByRole("article", { name: "Amal Haddad" }));
    const schedule = amal.getByRole("button", { name: "Schedule" });
    await waitFor(() => expect(schedule).toHaveAttribute("title", "Connect your mailbox to book a call"));

    await userEvent.click(schedule);

    expect(schedule).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByRole("dialog", { name: /^Book a call/ })).not.toBeInTheDocument();
    expect(amal.getByRole("button", { name: "Email" })).not.toHaveAttribute("aria-disabled");
  });

  describe("on a catalog whose ways out sit under each stage", () => {
    const current = aCurrentCatalog();
    const flaggedRows: PipelineRow[] = [
      aPipelineRow({ candidateId: "a", status: "contacted", fullName: "Amal Haddad" }, current),
      aPipelineRow({ candidateId: "b", status: "notInterested", fullName: "Bilal Saeed" }, current),
      aPipelineRow({ candidateId: "c", status: "identified", fullName: "Carla Mendes" }, current),
    ];

    beforeEach(() => {
      vi.mocked(catalogApi.pipelineCatalog).mockResolvedValue(current);
      vi.mocked(pipelineApi.getPipeline).mockResolvedValue(aPipelinePage(flaggedRows, current));
    });

    it("draws someone at a status out of process in the band under their stage, not in its column", async () => {
      renderView();
      const approach = await screen.findByRole("region", { name: "Approach" });

      expect(within(approach).getByRole("article", { name: "Amal Haddad" })).toBeInTheDocument();
      expect(within(approach).queryByRole("article", { name: "Bilal Saeed" })).not.toBeInTheDocument();
      const cell = within(screen.getByRole("group", { name: "Out of process · Approach" }));
      expect(cell.getByRole("article", { name: "Bilal Saeed" })).toBeInTheDocument();
      expect(cell.getByText(/^Not interested ·/)).toBeInTheDocument();
      expect(screen.queryByRole("group", { name: "Out of process · Declined" })).not.toBeInTheDocument();
    });

    it("asks which of a stage's ways out a card dropped in its cell takes, offering that stage's alone", async () => {
      vi.mocked(candidatesApi.changeCandidateStatus).mockReturnValue(new Promise(() => {}));
      renderView();
      await screen.findByRole("region", { name: "Approach" });

      dropOn("Amal Haddad", screen.getByRole("group", { name: "Out of process · Approach" }));

      const chooser = within(screen.getByRole("group", { name: "Mark Amal Haddad out of process" }));
      expect(chooser.getAllByRole("button").map((button) => button.textContent)).toEqual([
        "Not interested",
        "Unresponsive",
        "Cancel",
      ]);
      await userEvent.click(chooser.getByRole("button", { name: "Unresponsive" }));

      await waitFor(() => expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "a", "unresponsive"));
    });

    it("gives a card dropped in a cell with one way out that way out at once", async () => {
      vi.mocked(candidatesApi.changeCandidateStatus).mockReturnValue(new Promise(() => {}));
      renderView();
      await screen.findByRole("region", { name: "Approach" });

      dropOn("Amal Haddad", screen.getByRole("group", { name: "Out of process · Shortlist" }));

      await waitFor(() => expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "a", "clientRejected"));
      expect(screen.queryByRole("group", { name: "Mark Amal Haddad out of process" })).not.toBeInTheDocument();
    });

    it("brings a card out of process dropped on its own stage back to that stage's default", async () => {
      vi.mocked(candidatesApi.changeCandidateStatus).mockReturnValue(new Promise(() => {}));
      renderView();
      const approach = await screen.findByRole("region", { name: "Approach" });

      dropOn("Bilal Saeed", approach);

      await waitFor(() => expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "b", "contacted"));
    });

    it("hides someone out of process from the client from their card in the band", async () => {
      vi.mocked(pipelineApi.setClientVisibility).mockReturnValue(new Promise(() => {}));
      renderView();
      await screen.findByRole("region", { name: "Approach" });

      await userEvent.click(screen.getByRole("switch", { name: "Visible to client: Bilal Saeed" }));

      expect(pipelineApi.setClientVisibility).toHaveBeenCalledWith("p1", "b", false);
      expect(screen.queryByRole("dialog", { name: /^Executive/ })).not.toBeInTheDocument();
    });
  });
});
