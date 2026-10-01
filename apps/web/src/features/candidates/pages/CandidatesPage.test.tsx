import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as poolApi from "../api/poolApi";
import type { CandidateTag, PersonRecord, PoolPage, PoolRow } from "../api/types";
import { CandidatesPage } from "./CandidatesPage";

vi.mock("../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/poolApi")>()),
  listPool: vi.fn(),
  exportPool: vi.fn(),
  getPerson: vi.fn(),
  setOwner: vi.fn(),
  setDoNotContact: vi.fn(),
  tagPerson: vi.fn(),
  untagPerson: vi.fn(),
  retagPeople: vi.fn(),
  assignOwners: vi.fn(),
  addToPosition: vi.fn(),
  getPoolNotes: vi.fn(),
  writePoolNote: vi.fn(),
  getPoolTimeline: vi.fn(),
  getActivityFeed: vi.fn(),
  tagCatalog: vi.fn(),
  createTag: vi.fn(),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../../lib/saveBlob", () => ({ saveBlob: vi.fn() }));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

const openToWork: CandidateTag = { id: "t1", label: "Open to work", colour: "green", retired: false, holders: 1 };
const referral: CandidateTag = { id: "t2", label: "Referral", colour: "violet", retired: false, holders: 0 };

const cfo: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Finance",
  clientLogoUrl: null,
  positionTitle: "Chief Financial Officer",
  stage: "MAPPING",
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
  createdAt: "2026-07-13T10:00:00Z",
};

const fatima: PoolRow = {
  personId: "person-1",
  fullName: "Fatima Al Mazrouei",
  title: "Group CFO",
  companyName: "Aldar Properties",
  locationCity: "Abu Dhabi",
  locationCountry: "United Arab Emirates",
  linkedinUrl: null,
  doNotContact: false,
  positions: [
    {
      candidateId: "c1",
      projectId: "p1",
      positionTitle: "Chief Financial Officer",
      status: "engaged",
      addedByUserId: "u1",
      addedByName: null,
      addedAt: "2026-09-01T10:00:00Z",
      source: "manual",
      workable: false,
    },
  ],
  tagIds: ["t1"],
  ownerUserId: null,
  lastActivity: {
    id: 9,
    kind: "STATUS_CHANGED",
    occurredAt: "2026-09-20T10:00:00Z",
    actorUserId: "u1",
    actorName: "Alok Kumar",
    actorAvatarUrl: null,
    personId: "person-1",
    personName: "Fatima Al Mazrouei",
    projectId: "p1",
    projectTitle: "Chief Financial Officer",
    details: { from: "contacted", to: "engaged" },
    noteExcerpt: null,
  },
};

const rajesh: PoolRow = {
  ...fatima,
  personId: "person-2",
  fullName: "Rajesh Menon",
  title: "Finance Director",
  companyName: "Emaar",
  doNotContact: true,
  tagIds: [],
  positions: [],
  lastActivity: null,
};

const page = (people: PoolRow[]): PoolPage => ({
  people,
  totalCount: people.length,
  viewCounts: { all: 2, mine: 0, active: 1, unplaced: 1 },
  poolSize: 2,
  countries: ["United Arab Emirates"],
});

const record: PersonRecord = {
  personId: "person-1",
  fullName: "Fatima Al Mazrouei",
  title: "Group CFO",
  companyName: "Aldar Properties",
  seniority: null,
  linkedinUrl: null,
  locationCity: "Abu Dhabi",
  locationCountry: "United Arab Emirates",
  nationality: null,
  gender: null,
  yearsExperience: null,
  summary: null,
  compensation: {
    currency: null,
    baseSalary: null,
    bonus: null,
    allowances: null,
    longTermIncentive: null,
    noticePeriod: null,
    allowanceLines: [],
    longTermIncentiveTypes: [],
  },
  career: [],
  contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
  positions: fatima.positions,
  ownerUserId: null,
  doNotContact: null,
  tagIds: ["t1"],
  source: "manual",
  addedAt: "2026-09-01T10:00:00Z",
  addedByUserId: "u1",
  addedByName: "Alok Kumar",
};

function renderPage(path = "/candidates") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <CandidatesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("CandidatesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(poolApi.listPool).mockResolvedValue(page([fatima, rajesh]));
    vi.mocked(poolApi.tagCatalog).mockResolvedValue([openToWork, referral]);
    vi.mocked(workspaceApi.members).mockResolvedValue([
      {
        memberId: "m1",
        userId: "u1",
        fullName: "Alok Kumar",
        email: "alok@nextwebspark.com",
        title: null,
        avatarUrl: null,
        roles: ["ADMIN"],
        joinedAt: null,
      },
    ]);
    vi.mocked(projectsApi.projects).mockResolvedValue([cfo]);
    vi.mocked(poolApi.getPerson).mockResolvedValue(record);
    vi.mocked(poolApi.getPoolNotes).mockResolvedValue([]);
    vi.mocked(poolApi.getPoolTimeline).mockResolvedValue({ entries: [], nextCursor: null });
    vi.mocked(poolApi.getActivityFeed).mockResolvedValue({ entries: [], nextCursor: null });
  });

  it("lists the pool with each person's positions, tags and latest line, and counts each quick view", async () => {
    renderPage();

    expect((await screen.findAllByText("Fatima Al Mazrouei")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Group CFO · Aldar Properties").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Chief Financial Officer · Engaged").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Open to work").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Marked Engaged on Chief Financial Officer").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Do not contact").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /Not in a position\s*1/ })).toBeInTheDocument();
    expect(await screen.findByText("2 people · shared across every position in NextWebSpark Search")).toBeInTheDocument();
  });

  it("asks the server again for a quick view, a tag filter or a sort, never filtering what it already has", async () => {
    renderPage();
    await screen.findAllByText("Fatima Al Mazrouei");

    await userEvent.click(screen.getByRole("button", { name: /Owned by me/ }));
    await waitFor(() =>
      expect(poolApi.listPool).toHaveBeenLastCalledWith(
        expect.objectContaining({ view: "mine" }),
        0,
        50,
        expect.anything(),
      ),
    );

    await userEvent.click(screen.getByRole("button", { name: /^Filters/ }));
    await userEvent.click(within(screen.getByRole("region", { name: "Filters" })).getByRole("checkbox", { name: /Referral/ }));
    await userEvent.click(screen.getByRole("radio", { name: "None of" }));
    await waitFor(() =>
      expect(poolApi.listPool).toHaveBeenLastCalledWith(
        expect.objectContaining({ tagIds: ["t2"], tagMatch: "none" }),
        0,
        50,
        expect.anything(),
      ),
    );
    expect(screen.getByText("None of: Referral")).toBeInTheDocument();
  });

  it("tags the people ticked through the selection bar", async () => {
    vi.mocked(poolApi.retagPeople).mockResolvedValue({ changed: 2 });
    renderPage();
    await screen.findAllByText("Fatima Al Mazrouei");

    await userEvent.click(screen.getByRole("checkbox", { name: "Select every person shown" }));
    expect(screen.getByRole("region", { name: "2 people selected" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Tag" }));
    const dialog = screen.getByRole("dialog", { name: /Tag 2 people/ });
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Referral/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Add 1 tag" }));

    await waitFor(() =>
      expect(poolApi.retagPeople).toHaveBeenCalledWith(["person-1", "person-2"], ["t2"], false),
    );
  });

  it("adds the people ticked to a position, saying who is already in it", async () => {
    vi.mocked(poolApi.addToPosition).mockResolvedValue({ added: 1, alreadyIn: 1 });
    renderPage();
    await screen.findAllByText("Fatima Al Mazrouei");

    await userEvent.click(screen.getByRole("checkbox", { name: "Select every person shown" }));
    await userEvent.click(screen.getByRole("button", { name: "Add to position" }));
    const dialog = screen.getByRole("dialog", { name: /Add 2 people to a position/ });
    await userEvent.click(within(dialog).getByRole("radio", { name: /Chief Financial Officer/ }));
    expect(within(dialog).getByText(/1 is already in this position and stays as is/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Add 1 person" }));

    await waitFor(() => expect(poolApi.addToPosition).toHaveBeenCalledWith("p1", ["person-1", "person-2"]));
  });

  it("opens a person's drawer and marks them do not contact", async () => {
    const marked: PersonRecord = {
      ...record,
      doNotContact: {
        reason: "Marked from the candidate drawer.",
        setByUserId: "u1",
        setByName: "Alok Kumar",
        setAt: "2026-09-22T17:05:00Z",
      },
    };
    vi.mocked(poolApi.setDoNotContact).mockResolvedValue(marked);
    vi.mocked(poolApi.getPerson).mockResolvedValueOnce(record).mockResolvedValue(marked);
    renderPage("/candidates?person=person-1");

    expect(await screen.findByRole("heading", { name: "Fatima Al Mazrouei" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Do not contact" }));

    await waitFor(() =>
      expect(poolApi.setDoNotContact).toHaveBeenCalledWith("person-1", true, "Marked from the candidate drawer."),
    );
    expect(await screen.findByRole("note")).toHaveTextContent("Set by Alok Kumar");
  });

  it("writes a note about the person rather than a position, unless one is picked", async () => {
    vi.mocked(poolApi.writePoolNote).mockResolvedValue({
      id: "n1",
      kind: "call",
      body: "Open to a move.",
      pinned: false,
      projectId: null,
      projectTitle: null,
      authorUserId: "u1",
      authorName: "Alok Kumar",
      authorAvatarUrl: null,
      createdAt: "2026-09-22T10:00:00Z",
      editedAt: null,
      editedByName: null,
      editable: true,
    });
    renderPage("/candidates?person=person-1&tab=notes");

    await userEvent.type(await screen.findByLabelText("New note"), "Open to a move.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    await waitFor(() =>
      expect(poolApi.writePoolNote).toHaveBeenCalledWith("person-1", {
        kind: "call",
        body: "Open to a move.",
        projectId: null,
      }),
    );

    await userEvent.selectOptions(screen.getByLabelText("Position this note is about"), "p1");
    await userEvent.type(screen.getByLabelText("New note"), "Asked about the package.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    await waitFor(() =>
      expect(poolApi.writePoolNote).toHaveBeenLastCalledWith(
        "person-1",
        expect.objectContaining({ projectId: "p1" }),
      ),
    );
  });

  it("reads the activity feed with whom each line is about, and opens that person on their timeline", async () => {
    vi.mocked(poolApi.getActivityFeed).mockResolvedValue({
      entries: [{ ...fatima.lastActivity!, id: 3, kind: "TAGGED", details: { tagId: "t1", tag: "Open to work" } }],
      nextCursor: null,
    });
    renderPage("/candidates?view=activity");

    expect(await screen.findByText("tagged Fatima Al Mazrouei Open to work")).toBeInTheDocument();
    await userEvent.click(screen.getByText("tagged Fatima Al Mazrouei Open to work"));
    expect(await screen.findByRole("tab", { name: /Timeline/, selected: true })).toBeInTheDocument();
  });

  it("says the workspace has nobody yet rather than drawing an empty grid", async () => {
    vi.mocked(poolApi.listPool).mockResolvedValue({ ...page([]), poolSize: 0, totalCount: 0 });
    renderPage();

    expect(await screen.findByText("No candidates yet")).toBeInTheDocument();
  });
});
