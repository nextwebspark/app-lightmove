import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { typeInto } from "../../../test/noteEditor";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskResponse } from "../../tasks/api/types";
import * as conversationApi from "../api/conversationApi";
import type { Conversation, ConversationList, ConversationRow } from "../api/conversationApi";
import * as mailboxApi from "../api/mailboxApi";
import type { Mailbox } from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import * as pipelineApi from "../../pipeline/api/pipelineApi";
import * as personCrmApi from "../../candidates/api/personCrmApi";
import * as projectsApi from "../../projects/api/projectsApi";
import { OutreachPage } from "./OutreachPage";

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  // The page holds the position's stream open; it stays silent unless a test speaks for it.
  streamEvents: vi.fn((_path: string, onEvent: (event: { name: string; data: string }) => void) => {
    streamListeners.push(onEvent);
    return new Promise<void>(() => {});
  }),
}));

const streamListeners: ((event: { name: string; data: string }) => void)[] = [];

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", workspace: { id: "w1", roles: ["MEMBER"] } } }),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
  startMailboxConnect: vi.fn(),
  sendMailboxTest: vi.fn(),
  disconnectMailbox: vi.fn(),
}));

vi.mock("../api/conversationApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/conversationApi")>()),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  sendMessage: vi.fn(),
  cancelMessage: vi.fn(),
}));

vi.mock("../api/sequenceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sequenceApi")>()),
  getSequences: vi.fn(),
  getEnrollmentCandidates: vi.fn(),
}));

vi.mock("../../pipeline/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../pipeline/api/pipelineApi")>()),
  getPipeline: vi.fn(),
}));

vi.mock("../../candidates/api/personCrmApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/personCrmApi")>()),
  getPersonPositions: vi.fn(),
}));

vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));

vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getCandidateTasks: vi.fn(),
  updateTask: vi.fn(),
}));

vi.mock("../../candidates/components/CandidateDrawerById", () => ({
  CandidateDrawerById: ({ candidateId }: { candidateId: string | null }) =>
    candidateId ? <div role="dialog" aria-label={`Drawer for ${candidateId}`} /> : null,
}));

const PROJECT = { id: "p1", positionTitle: "Group CFO", archivedAt: null };
/** Fatima open, as a click on her row leaves the address. */
const OPEN = "/projects/p1/outreach?candidate=c1";

const nothingConnected: Mailbox = { offered: true, providers: ["google", "microsoft"], connection: null, bookingLinkOffered: false };
const connected: Mailbox = {
  ...nothingConnected,
  connection: {
    address: "yara@firm.example",
    provider: "google",
    status: "ACTIVE",
    dailyCap: 50,
    timeZone: "Asia/Dubai",
    connectedAt: "2026-10-01T09:00:00Z",
    movesOffNylas: false,
    runsStoppedByMove: 0,
    liveSequences: 0,
    livePeople: 0,
    readsReplies: true,
    reconnectToReadReplies: false,
    sentToday: 3,
    scheduledEmails: 0,
  },
};

function row(overrides: Partial<ConversationRow>): ConversationRow {
  return {
    candidateId: "c1",
    personId: "person-1",
    fullName: "Fatima Al Mazrouei",
    title: "CFO",
    companyName: "Gulf Ports",
    enrichedAt: null,
    status: "interested",
    statusLabel: "Interested",
    statusColour: "green",
    group: "NEEDS_REPLY",
    action: {
      taskId: "t1",
      title: "Reply to Fatima",
      kind: "email",
      rule: "reply",
      dueAt: null,
      assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
    },
    run: null,
    latest: { channel: "EMAIL", direction: "INBOUND", kind: "REPLY", at: new Date().toISOString(), preview: "Thursday could work." },
    lastSentAt: "2026-10-05T05:00:00Z",
    scheduledAt: null,
    lastActivityAt: new Date().toISOString(),
    ...overrides,
  };
}

const QUEUE: ConversationList = {
  counts: { needs: 2, scheduled: 1 },
  nextScheduledAt: "2026-10-22T05:00:00Z",
  rows: [
    row({}),
    row({
      candidateId: "c2",
      personId: "person-2",
      fullName: "Noura Al Thani",
      group: "ATTENTION",
      action: null,
      latest: { channel: "EMAIL", direction: "INBOUND", kind: "BOUNCE", at: "2026-10-20T05:00:00Z", preview: null },
    }),
  ],
  truncated: false,
};

const NOBODY: ConversationList = { counts: { needs: 0, scheduled: 0 }, nextScheduledAt: null, rows: [], truncated: false };

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    executive: {
      candidateId: "c1",
      personId: "person-1",
      fullName: "Fatima Al Mazrouei",
      title: "CFO",
      companyName: "Gulf Ports",
      triageCompanyId: null,
      status: "interested",
      statusLabel: "Interested",
      statusColour: "green",
      outOfProcess: false,
      pipelineStage: { key: "interested", label: "Interested", colour: "green", position: 3 },
      addedAt: "2026-09-20T05:00:00Z",
      statusChangedAt: "2026-10-01T05:00:00Z",
      nextAction: null,
      clientVisible: false,
    },
    openTasks: [
      {
        taskId: "t1",
        title: "Reply to Fatima",
        kind: "email",
        rule: "reply",
        dueAt: null,
        assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
      },
    ],
    currentRun: { run: null, steps: [] },
    keyDates: { addedAt: "2026-09-20T05:00:00Z", firstContactedAt: "2026-10-01T05:00:00Z", repliedAt: null, lastActivityAt: null },
    contact: {
      emails: [{ address: "fatima@gulfports.example", kind: "work", verified: true } as never],
      phones: [],
      locationCity: "Dubai",
      locationCountry: "United Arab Emirates",
      doNotContact: false,
    },
    composer: { replySubject: "Re: A confidential conversation", replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } },
    events: [
      {
        type: "MESSAGE",
        at: "2026-10-20T06:00:00Z",
        message: message({ direction: "INBOUND", kind: "REPLY", status: "RECEIVED", text: "<b>Thursday</b> could work." }),
      },
      {
        type: "MESSAGE",
        at: "2026-10-01T05:00:00Z",
        message: message({ subject: "A confidential conversation", text: "Hi **Fatima**", format: "TEXT", sequenceName: "CFO — first approach", stepNumber: 1, stepCount: 3 }),
      },
    ],
    truncated: false,
    ...overrides,
  };
}

function message(overrides: Partial<conversationApi.ConversationMessage>): conversationApi.ConversationMessage {
  return {
    id: `m-${Math.random()}`,
    direction: "OUTBOUND",
    channel: "EMAIL",
    kind: "SEQUENCE_STEP",
    status: "SENT",
    at: "2026-10-01T05:00:00Z",
    subject: null,
    text: null,
    format: "TEXT",
    fromAddress: "yara@firm.example",
    toAddress: "fatima@gulfports.example",
    senderUserId: "u1",
    senderName: "Yara Haddad",
    enrollmentId: null,
    sequenceName: null,
    stepNumber: null,
    stepCount: null,
    failReason: null,
    attachments: [],
    ...overrides,
  };
}

function replyTask(): TaskResponse {
  return {
    id: "t1",
    projectId: "p1",
    projectTitle: "Group CFO",
    candidateId: "c1",
    personId: "person-1",
    personName: "Fatima Al Mazrouei",
    title: "Reply to Fatima",
    kind: "email",
    detail: null,
    presetId: null,
    note: null,
    assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
    createdBy: null,
    dueAt: null,
    status: "open",
    source: "rule",
    rule: "reply",
    completedAt: null,
    completedBy: null,
    createdAt: "2026-10-20T06:00:00Z",
    editable: true,
  };
}

function Where() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output aria-label="Location">{location.search}</output>
      <button type="button" onClick={() => navigate(-1)}>
        Browser back
      </button>
    </>
  );
}

function renderPage(path = "/projects/p1/outreach") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project: PROJECT }} />}>
              <Route
                path="*"
                element={
                  <>
                    <OutreachPage />
                    <Where />
                  </>
                }
              />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** The dock's first button — Reply, or Write email — and the dialog it opens. */
async function openEmail(): Promise<HTMLElement> {
  const dock = await screen.findByRole("toolbar", { name: "Conversation actions" });
  await userEvent.click(within(dock).getAllByRole("button")[0]);
  return screen.findByRole("dialog", { name: "Email Fatima Al Mazrouei" });
}

describe("OutreachPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    streamListeners.length = 0;
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(conversationApi.listConversations).mockResolvedValue(QUEUE);
    vi.mocked(conversationApi.getConversation).mockResolvedValue(conversation());
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [replyTask()], done: [], doneCount: 0 });
    vi.mocked(personCrmApi.getPersonPositions).mockResolvedValue([]);
    vi.mocked(projectsApi.projects).mockResolvedValue([{ ...PROJECT, team: [] }] as never);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lands on everyone in conversation with the newest open beside the list, and opens whoever is picked", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Fatima Al Mazrouei" })).toBeInTheDocument();
    expect(conversationApi.listConversations).toHaveBeenCalledWith("p1", "all", "team", "", expect.anything());
    expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c1");
    expect(screen.getByText("SYNCED EMAIL")).toBeInTheDocument();
    expect(screen.getByText("SENT EMAIL")).toBeInTheDocument();
    expect(screen.getByText(/CFO — first approach · Step 1 of 3/)).toBeInTheDocument();
    expect(await screen.findByText("Required next action")).toBeInTheDocument();

    const list = screen.getByRole("region", { name: "Conversations" });
    await userEvent.click(within(list).getByRole("button", { name: /Noura Al Thani/ }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c2"));
    expect(conversationApi.getConversation).toHaveBeenLastCalledWith("p1", "c2", expect.anything());
  });

  it("on a phone, lands on the list with nobody open", async () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }));
    renderPage();

    const list = await screen.findByRole("region", { name: "Conversations" });
    expect(await within(list).findByRole("button", { name: /Fatima Al Mazrouei/ })).toBeInTheDocument();
    await new Promise((settle) => setTimeout(settle, 0));
    expect(screen.getByLabelText("Location")).not.toHaveTextContent("candidate=");
    expect(conversationApi.getConversation).not.toHaveBeenCalled();
  });

  it("narrows to To do, grouped by what each person is waiting on", async () => {
    renderPage();
    const list = await screen.findByRole("region", { name: "Conversations" });
    expect(await within(list).findByRole("button", { name: /To do/ })).toHaveTextContent("2");

    await userEvent.click(within(list).getByRole("button", { name: /To do/ }));

    await waitFor(() => expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "needs", "team", "", expect.anything()));
    expect(within(list).getByText("Needs reply")).toBeInTheDocument();
    expect(within(list).getByText("Delivery problems")).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: /To do/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Location")).toHaveTextContent("view=needs");
  });

  it("says a To do with nothing in it is all caught up, and leads back to everyone", async () => {
    vi.mocked(conversationApi.listConversations).mockImplementation(async (_projectId, view) => (view === "needs" ? NOBODY : QUEUE));
    renderPage("/projects/p1/outreach?view=needs");

    expect(await screen.findByRole("heading", { name: "All caught up" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show all conversations" }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c1"));
    expect(screen.getByLabelText("Location")).not.toHaveTextContent("view=");
  });

  it("offers to clear a search that matches nobody", async () => {
    vi.mocked(conversationApi.listConversations).mockImplementation(async (_projectId, _view, _who, query) =>
      query === "zzz" ? NOBODY : QUEUE,
    );
    renderPage("/projects/p1/outreach?q=zzz");

    expect(await screen.findByRole("heading", { name: "No one matches “zzz”" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Clear search" }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c1"));
    expect(screen.getByLabelText("Location")).not.toHaveTextContent("q=");
  });

  it("writes to anyone on the position from New email: the newest offered, anyone found by typing", async () => {
    vi.mocked(pipelineApi.getPipeline).mockResolvedValue({
      rows: [
        { candidateId: "c1", personId: "person-1", fullName: "Fatima Al Mazrouei", title: "CFO", companyName: "Gulf Ports", statusLabel: "Interested", addedAt: "2026-09-20T05:00:00Z" },
        { candidateId: "c9", personId: "person-9", fullName: "Khalid Nasser", title: "Treasurer", companyName: "Emaar", statusLabel: "Identified", addedAt: "2026-10-20T05:00:00Z" },
      ] as never,
      stageCounts: [],
      inactiveCount: 0,
      total: 2,
    });
    renderPage();
    expect(screen.queryByRole("link", { name: "New sequence" })).not.toBeInTheDocument();

    await userEvent.click(await screen.findByRole("button", { name: "New email" }));
    const people = await screen.findByRole("listbox", { name: "People on this position" });
    expect(screen.getByText("Recently added")).toBeInTheDocument();
    expect(within(people).getAllByRole("option").map((option) => option.textContent)).toEqual([
      expect.stringContaining("Khalid Nasser"),
      expect.stringContaining("Fatima Al Mazrouei"),
    ]);
    await userEvent.type(screen.getByRole("combobox", { name: "Who are you writing to?" }), "emaar");
    expect(within(people).queryByRole("option", { name: /Fatima Al Mazrouei/ })).not.toBeInTheDocument();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c9"));
    expect(conversationApi.getConversation).toHaveBeenCalledWith("p1", "c9", expect.anything());
    expect(await screen.findByRole("dialog", { name: "Email Fatima Al Mazrouei" })).toBeInTheDocument();
  });

  it("with no mailbox, keeps New email quiet and offers the connection instead", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    vi.stubGlobal("open", vi.fn(() => ({ location: { href: "about:blank" }, closed: false, close: vi.fn() })));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "New email" })).toHaveAttribute("title", "Connect your mailbox first"),
    );
    await userEvent.click(screen.getByRole("button", { name: "New email" }));
    const connect = await screen.findByRole("group", { name: "Connect your mailbox first" });
    await userEvent.click(within(connect).getByRole("button", { name: "Connect Gmail" }));

    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
    expect(pipelineApi.getPipeline).not.toHaveBeenCalled();
  });

  it("points a position nobody has written to at where people are added", async () => {
    vi.mocked(conversationApi.listConversations).mockResolvedValue(NOBODY);
    renderPage();

    expect(await screen.findByRole("heading", { name: "No conversations yet" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "In universe" })).toHaveAttribute("href", "/projects/p1/companies/universe");
    expect(screen.getByRole("link", { name: "Pipeline" })).toHaveAttribute("href", "/projects/p1/companies/universe?view=pipeline");
  });

  it("reads the queue and the open conversation again the moment the stream says outreach moved", async () => {
    renderPage();
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });
    const queueReads = vi.mocked(conversationApi.listConversations).mock.calls.length;
    const conversationReads = vi.mocked(conversationApi.getConversation).mock.calls.length;

    act(() => streamListeners.forEach((emit) => emit({ name: "connected", data: "{}" })));
    act(() => streamListeners.forEach((emit) => emit({ name: "change", data: '{"kind":"outreach"}' })));

    await waitFor(() => expect(vi.mocked(conversationApi.listConversations).mock.calls.length).toBeGreaterThan(queueReads));
    expect(vi.mocked(conversationApi.getConversation).mock.calls.length).toBeGreaterThan(conversationReads);
  });

  it("draws a reply's words as text, never as markup", async () => {
    renderPage(OPEN);

    expect(await screen.findByText("<b>Thursday</b> could work.")).toBeInTheDocument();
    expect(document.querySelector("b")).toBeNull();
  });

  it("keeps the filter, the scope and the search in the address, and a new filter opens the top of its own list", async () => {
    const scheduled: ConversationList = { ...QUEUE, rows: [QUEUE.rows[1]] };
    vi.mocked(conversationApi.listConversations).mockImplementation(async (_projectId, view) =>
      view === "scheduled" ? scheduled : QUEUE,
    );
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(screen.getByRole("button", { name: /^Scheduled/ }));
    await waitFor(() => expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "scheduled", "team", "", expect.anything()));
    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c2"));
    await userEvent.click(screen.getByRole("radio", { name: "Mine" }));
    await waitFor(() => expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "scheduled", "mine", "", expect.anything()));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search people or emails" }), "Noura");

    await waitFor(() =>
      expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "scheduled", "mine", "Noura", expect.anything()),
    );
    expect(screen.getByLabelText("Location")).toHaveTextContent("view=scheduled");
    expect(screen.getByLabelText("Location")).toHaveTextContent("who=mine");
  });

  it("brings the search back with the address on Back, and never writes the old one over it", async () => {
    renderPage();
    const search = await screen.findByRole("searchbox", { name: "Search people or emails" });
    await userEvent.type(search, "Noura");
    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("q=Noura"));
    await userEvent.click(await screen.findByRole("button", { name: /Noura Al Thani/ }));
    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c2"));
    await userEvent.clear(search);
    await waitFor(() => expect(screen.getByLabelText("Location")).not.toHaveTextContent("q="));

    await userEvent.click(screen.getByRole("button", { name: "Browser back" }));

    await waitFor(() => expect(search).toHaveValue("Noura"));
    await new Promise((settle) => setTimeout(settle, 400));
    expect(screen.getByLabelText("Location")).toHaveTextContent("q=Noura");
  });

  it("sends a reply into the thread, saying first what sending does", async () => {
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m9",
      status: "SCHEDULED",
      sendAt: new Date().toISOString(),
      subject: "Re: A confidential conversation",
      inThread: true,
    });
    renderPage(OPEN);
    await openEmail();

    // The composer's own Send now: until the mailbox is read, a locked composer stands in its place.
    await screen.findByRole("button", { name: "Send now" });
    const composer = screen.getByRole("region", { name: "Reply" });
    expect(within(composer).getByText("Reply to Fatima")).toBeInTheDocument();
    expect(within(composer).getByText("Re: A confidential conversation")).toBeInTheDocument();
    expect(within(composer).getByText("Sending resolves this reply task. Pipeline stage stays Interested.")).toBeInTheDocument();
    await typeInto(await within(composer).findByRole("textbox", { name: "Message" }), "Thursday at 10?");
    await userEvent.click(within(composer).getByRole("button", { name: "Send now" }));

    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith("p1", "c1", {
        toAddress: "fatima@gulfports.example",
        subject: null,
        body: "Thursday at 10?",
        sendAt: null,
        replyInThread: true,
        documentVersionIds: [],
      }, []),
    );
    expect(await screen.findByText("Goes to Fatima within a minute.")).toBeInTheDocument();
  });

  it("writes a first email, with a subject of its own, where there is no thread to reply in", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(
      conversation({ composer: { replySubject: null, replyStopsRun: false, emailSentMovesTo: { statusKey: "contacted", label: "Contacted", colour: "accent" }, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } }),
    );
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m9",
      status: "SCHEDULED",
      sendAt: new Date().toISOString(),
      subject: "A conversation",
      inThread: false,
    });
    renderPage(OPEN);
    await openEmail();

    await screen.findByRole("button", { name: "Send now" });
    const composer = screen.getByRole("region", { name: "Reply" });
    expect(within(composer).getByText("First email to Fatima")).toBeInTheDocument();
    expect(within(composer).getByText(/Moves to Contacted\. Nothing is sent until you press Send now or Schedule\./)).toBeInTheDocument();
    await typeInto(await within(composer).findByRole("textbox", { name: "Message" }), "Hello");
    expect(within(composer).getByRole("button", { name: "Send now" })).toBeDisabled();
    await userEvent.type(within(composer).getByRole("textbox", { name: "Subject" }), "A conversation");
    await userEvent.click(within(composer).getByRole("button", { name: "Send now" }));

    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ subject: "A conversation", replyInThread: false }),
        [],
      ),
    );
  });

  it("locks the composer for someone marked do not contact", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(
      conversation({ contact: { ...conversation().contact, doNotContact: true } }),
    );
    renderPage(OPEN);
    await openEmail();

    await screen.findByText("Fatima is marked do not contact.");
    const composer = screen.getByRole("region", { name: "Reply" });
    expect(within(composer).getByText("Fatima is marked do not contact.")).toBeInTheDocument();
    expect(within(composer).queryByRole("button", { name: "Send now" })).not.toBeInTheDocument();
  });

  it("with no mailbox and nobody to open, offers one way in where the conversation goes, and no banner", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(conversationApi.listConversations).mockResolvedValue(NOBODY);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    const popup = { location: { href: "about:blank" }, closed: false, close: vi.fn() };
    vi.stubGlobal("open", vi.fn(() => popup));
    renderPage();

    expect(await screen.findByRole("heading", { name: "Connect your mailbox" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect mailbox" })).toBeInTheDocument();
    expect(screen.queryByText(/send and receive email from Uncava/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Connect Outlook" }));

    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("microsoft");
    await waitFor(() => expect(popup.location.href).toBe("https://mail.example/connect?state=s"));
  });

  it("with no mailbox, still opens the top conversation, its composer locked and connecting from there", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    const popup = { location: { href: "about:blank" }, closed: false, close: vi.fn() };
    const open = vi.fn(() => popup);
    vi.stubGlobal("open", open);
    renderPage();
    await openEmail();

    await screen.findByText("Connect your mailbox to write from here. Emails go from your own address.");
    const composer = screen.getByRole("region", { name: "Reply" });
    await userEvent.click(within(composer).getByRole("button", { name: "Connect Gmail" }));

    expect(open).toHaveBeenCalledWith("about:blank", "_blank", expect.any(String));
    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
    await waitFor(() => expect(popup.location.href).toBe("https://mail.example/connect?state=s"));
  });

  it("shows the mailbox in the header, sends a test from it and counts what a disconnect stops", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, liveSequences: 2, livePeople: 5, scheduledEmails: 1 },
    });
    vi.mocked(mailboxApi.sendMailboxTest).mockResolvedValue(undefined);
    vi.mocked(mailboxApi.disconnectMailbox).mockResolvedValue(undefined);
    renderPage();

    const chip = await screen.findByRole("button", { name: "Mailbox yara@firm.example" });
    expect(chip).toHaveTextContent("3 of 50 today");
    await userEvent.click(chip);
    expect(screen.queryByRole("combobox", { name: "Sending time zone" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send a test email" }));
    expect(await screen.findByText("Test email sent. Check your inbox.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Disconnect" }));
    const dialog = await screen.findByRole("dialog", { name: "Disconnect yara@firm.example?" });
    expect(dialog).toHaveTextContent("2 live sequences (5 people) will stop sending, and 1 scheduled email won't go unless you reconnect before it is due.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Disconnect" }));
    await waitFor(() => expect(mailboxApi.disconnectMailbox).toHaveBeenCalled());
  });

  it("asks for a reconnect when the provider withdrew access", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ ...connected, connection: { ...connected.connection!, status: "ERROR" } });
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Gmail disconnected.");
  });

  it("offers a Nylas mailbox the move to Uncava's own connection, saying which runs it stops", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, movesOffNylas: true, runsStoppedByMove: 2 },
    });
    renderPage();

    expect(await screen.findByText("Reconnect your mailbox.")).toBeInTheDocument();
    expect(screen.getByText(/2 running sequences stop at their next email/)).toBeInTheDocument();
  });

  it("asks a mailbox whose consent stopped short of replies to reconnect, while it keeps sending", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, reconnectToReadReplies: true },
    });
    renderPage(OPEN);
    await openEmail();

    expect(await screen.findByText("Reconnect to show replies here.")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Send now" })).toBeInTheDocument();
  });

  it("says outreach is not set up where the deployment has no mail service", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ offered: false, providers: [], connection: null, bookingLinkOffered: false });
    vi.mocked(conversationApi.listConversations).mockResolvedValue(NOBODY);
    renderPage();

    expect(await screen.findByRole("heading", { name: "Outreach email is off for your workspace" })).toBeInTheDocument();
    expect(screen.getByText("Ask Uncava support to turn it on.")).toBeInTheDocument();
  });

  it("cancels an email its sender scheduled", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(
      conversation({
        events: [
          {
            type: "MESSAGE",
            at: "2026-10-30T05:00:00Z",
            message: message({ id: "m7", kind: "MANUAL", status: "SCHEDULED", subject: "Later", text: "Hello" }),
          },
        ],
      }),
    );
    vi.mocked(conversationApi.cancelMessage).mockResolvedValue(undefined);
    renderPage(OPEN);

    expect(await screen.findByText("SCHEDULED")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(conversationApi.cancelMessage).toHaveBeenCalledWith("p1", "m7"));
  });

  it("on To do, marks the reply task handled and moves on to the next person", async () => {
    vi.mocked(tasksApi.updateTask).mockResolvedValue({ ...replyTask(), status: "done" });
    renderPage("/projects/p1/outreach?view=needs&candidate=c1");
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(await screen.findByRole("button", { name: "Mark handled" }));

    await waitFor(() => expect(tasksApi.updateTask).toHaveBeenCalledWith("t1", { status: "done" }));
    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c2"));
  });

  it("on To do, handling the last one leaves everyone caught up rather than reopening them", async () => {
    let isHandled = false;
    vi.mocked(tasksApi.updateTask).mockImplementation(async () => {
      isHandled = true;
      return { ...replyTask(), status: "done" };
    });
    vi.mocked(conversationApi.listConversations).mockImplementation(async (_projectId, view) =>
      view !== "needs" ? QUEUE : isHandled ? NOBODY : { ...QUEUE, rows: [QUEUE.rows[0]] },
    );
    renderPage("/projects/p1/outreach?view=needs&candidate=c1");
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(await screen.findByRole("button", { name: "Mark handled" }));

    expect(await screen.findByRole("heading", { name: "All caught up" })).toBeInTheDocument();
    expect(screen.getByLabelText("Location")).not.toHaveTextContent("candidate=");
  });

  it("anywhere but To do, keeps the person open once their task is handled", async () => {
    vi.mocked(tasksApi.updateTask).mockResolvedValue({ ...replyTask(), status: "done" });
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(await screen.findByRole("button", { name: "Mark handled" }));

    await waitFor(() => expect(tasksApi.updateTask).toHaveBeenCalledWith("t1", { status: "done" }));
    expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c1");
  });

  it("logs a call from the dock, and Expand grows that dialog rather than opening a second: the kind, who reads it, the save", async () => {
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(screen.getByRole("button", { name: "Log call" }));
    const dialog = await screen.findByRole("dialog", { name: "Log a call with Fatima Al Mazrouei" });
    expect(within(dialog).getByRole("radio", { name: "Call" })).toHaveAttribute("aria-checked", "true");
    await within(dialog).findByRole("textbox", { name: "New note" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Expand" }));

    expect(screen.getAllByRole("dialog")).toEqual([dialog]);
    expect(within(dialog).getByRole("radiogroup", { name: "Kind of note" })).toBeInTheDocument();
    expect(within(dialog).getByRole("radiogroup", { name: "Who reads this note" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Save note" })).toBeInTheDocument();
    expect(within(dialog).queryByText("Editing in the larger view…")).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Collapse" }));
    expect(within(dialog).getByRole("button", { name: "Expand" })).toBeInTheDocument();
  });

  it("logs a LinkedIn message from the dock, filed as LinkedIn", async () => {
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(screen.getByRole("button", { name: "Log LinkedIn" }));
    const dialog = await screen.findByRole("dialog", { name: "Log a LinkedIn message with Fatima Al Mazrouei" });
    expect(within(dialog).getByRole("radio", { name: "LinkedIn" })).toHaveAttribute("aria-checked", "true");
  });

  it("holds a note being written against Escape, as it does an email", async () => {
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(screen.getByRole("button", { name: "Add note" }));
    const dialog = await screen.findByRole("dialog", { name: "Add a note on Fatima Al Mazrouei" });
    await typeInto(await within(dialog).findByRole("textbox", { name: "New note" }), "Prefers mornings");
    await userEvent.keyboard("{Escape}");

    expect(screen.getByRole("dialog", { name: "Add a note on Fatima Al Mazrouei" })).toBeInTheDocument();
  });

  it("grows the email dialog on Expand, sends from it, and closes it", async () => {
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m9",
      status: "SCHEDULED",
      sendAt: new Date().toISOString(),
      subject: "Re: A confidential conversation",
      inThread: true,
    });
    renderPage(OPEN);
    const dialog = await openEmail();
    await typeInto(await within(dialog).findByRole("textbox", { name: "Message" }), "Thursday at 10?");

    await userEvent.click(within(dialog).getByRole("button", { name: "Expand" }));
    expect(screen.getAllByRole("dialog")).toEqual([dialog]);
    expect(within(dialog).getByText("Re: A confidential conversation")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Schedule" })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Send now" }));

    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ body: "Thursday at 10?", replyInThread: true }),
        [],
      ),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("holds an email being written against Escape, and lets the close button go", async () => {
    renderPage(OPEN);
    const dialog = await openEmail();
    await typeInto(await within(dialog).findByRole("textbox", { name: "Message" }), "Thursday at 10?");

    await userEvent.keyboard("{Escape}");
    expect(screen.getByRole("dialog", { name: "Email Fatima Al Mazrouei" })).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog", { name: "Email Fatima Al Mazrouei" })).not.toBeInTheDocument();
  });

  it("opens on the history, counting what has passed, and writes only when asked", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(conversation({ openTasks: [] }));
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [], done: [], doneCount: 0 });
    renderPage(OPEN);
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    const glance = await screen.findByRole("list", { name: "At a glance" });
    expect(glance).toHaveTextContent("1 sent");
    expect(glance).toHaveTextContent("1 reply");
    await waitFor(() => expect(tasksApi.getCandidateTasks).toHaveBeenCalled());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark handled" })).not.toBeInTheDocument();

    const dialog = await openEmail();
    expect(within(dialog).getByRole("region", { name: "Reply" })).toBeInTheDocument();
  });

  it("adds an open person to a sequence from the dock, and only from there", async () => {
    vi.mocked(sequenceApi.getSequences).mockReturnValue(new Promise(() => {}));
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockReturnValue(new Promise(() => {}));
    renderPage(OPEN);

    const dock = await screen.findByRole("toolbar", { name: "Conversation actions" });
    await userEvent.click(await within(dock).findByRole("button", { name: "Add to sequence" }));

    expect(await screen.findByRole("dialog", { name: "Add to sequence" })).toBeInTheDocument();
    for (const details of screen.getAllByRole("complementary", { name: "Candidate details" })) {
      expect(within(details).queryByRole("button", { name: /sequence/i })).not.toBeInTheDocument();
    }
  });

  it("says why when the list is refused, and counts nothing", async () => {
    vi.mocked(conversationApi.listConversations).mockRejectedValue(
      Object.assign(new Error("Forbidden"), { problem: { code: "ACCESS_DENIED", status: 403 } }),
    );
    renderPage();

    const list = await screen.findByRole("region", { name: "Conversations" });
    expect(await within(list).findByRole("alert")).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: "To do" })).not.toHaveTextContent(/\d/);
    expect(screen.queryByRole("heading", { name: "No conversations yet" })).not.toBeInTheDocument();
  });
});
