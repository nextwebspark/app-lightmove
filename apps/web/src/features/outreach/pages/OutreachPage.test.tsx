import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
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
import { OutreachPage } from "./OutreachPage";

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
      pipelineStage: { key: "interested", label: "Interested", colour: "green", kind: "active", position: 3 },
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
    composer: { replySubject: "Re: A confidential conversation", replyStopsRun: false, emailSentMovesTo: null },
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

describe("OutreachPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(conversationApi.listConversations).mockResolvedValue(QUEUE);
    vi.mocked(conversationApi.getConversation).mockResolvedValue(conversation());
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [replyTask()], done: [], doneCount: 0 });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("groups the queue, opens the first person and draws their history as the mockup tags it", async () => {
    renderPage();

    const queue = await screen.findByRole("region", { name: "Action queue" });
    expect(await within(queue).findByText("Needs reply")).toBeInTheDocument();
    expect(within(queue).getByText("Attention")).toBeInTheDocument();
    expect(within(queue).getByText("2 open")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Needs action/ })).toHaveTextContent("2");

    expect(await screen.findByRole("heading", { name: "Fatima Al Mazrouei" })).toBeInTheDocument();
    expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c1");
    expect(screen.getByText("SYNCED EMAIL")).toBeInTheDocument();
    expect(screen.getByText("SENT EMAIL")).toBeInTheDocument();
    expect(screen.getByText(/CFO — first approach · Step 1 of 3/)).toBeInTheDocument();
    expect(await screen.findByText("Required next action")).toBeInTheDocument();
  });

  it("draws a reply's words as text, never as markup", async () => {
    renderPage();

    expect(await screen.findByText("<b>Thursday</b> could work.")).toBeInTheDocument();
    expect(document.querySelector("b")).toBeNull();
  });

  it("keeps the tab, the scope and the search in the address", async () => {
    renderPage();
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(screen.getByRole("tab", { name: "All conversations" }));
    await waitFor(() => expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "all", "mine", "", expect.anything()));
    await userEvent.click(screen.getByRole("radio", { name: "Team" }));
    await waitFor(() => expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "all", "team", "", expect.anything()));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search people or messages" }), "Noura");

    await waitFor(() =>
      expect(conversationApi.listConversations).toHaveBeenLastCalledWith("p1", "all", "team", "Noura", expect.anything()),
    );
    expect(screen.getByLabelText("Location")).toHaveTextContent("view=all");
    expect(screen.getByLabelText("Location")).toHaveTextContent("who=team");
  });

  it("brings the search back with the address on Back, and never writes the old one over it", async () => {
    renderPage();
    const search = await screen.findByRole("searchbox", { name: "Search people or messages" });
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

  it("goes to the Scheduled tab from the queue's footer", async () => {
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /1 scheduled/ }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("view=scheduled"));
  });

  it("sends a reply into the thread, saying first what sending does", async () => {
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m9",
      status: "SCHEDULED",
      sendAt: new Date().toISOString(),
      subject: "Re: A confidential conversation",
      inThread: true,
    });
    renderPage();

    const composer = await screen.findByRole("region", { name: "Reply" });
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
      }),
    );
    expect(await screen.findByText("Goes to Fatima within a minute.")).toBeInTheDocument();
  });

  it("writes a first email, with a subject of its own, where there is no thread to reply in", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(
      conversation({ composer: { replySubject: null, replyStopsRun: false, emailSentMovesTo: { statusKey: "contacted", label: "Contacted", colour: "accent" } } }),
    );
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m9",
      status: "SCHEDULED",
      sendAt: new Date().toISOString(),
      subject: "A conversation",
      inThread: false,
    });
    renderPage();

    const composer = await screen.findByRole("region", { name: "Reply" });
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
      ),
    );
  });

  it("locks the composer for someone marked do not contact", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue(
      conversation({ contact: { ...conversation().contact, doNotContact: true } }),
    );
    renderPage();

    const composer = await screen.findByRole("region", { name: "Reply" });
    expect(within(composer).getByText("Fatima is marked do not contact.")).toBeInTheDocument();
    expect(within(composer).queryByRole("button", { name: "Send now" })).not.toBeInTheDocument();
  });

  it("with no mailbox, says so above the panes and in the composer, and connects from either", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    const popup = { location: { href: "about:blank" }, closed: false, close: vi.fn() };
    const open = vi.fn(() => popup);
    vi.stubGlobal("open", open);
    renderPage();

    expect(await screen.findByText("Connect your mailbox to send and receive email from Uncava")).toBeInTheDocument();
    const composer = await screen.findByRole("region", { name: "Reply" });
    expect(within(composer).getByText("Connect your mailbox to write from here. Emails go from your own address.")).toBeInTheDocument();
    await userEvent.click(within(composer).getByRole("button", { name: "Connect Gmail" }));

    expect(open).toHaveBeenCalledWith("about:blank", "_blank", expect.any(String));
    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
    await waitFor(() => expect(popup.location.href).toBe("https://mail.example/connect?state=s"));
  });

  it("shows the mailbox in the tab row, sends a test from it and counts what a disconnect stops", async () => {
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
    renderPage();

    expect(await screen.findByText("Reconnect to show replies here.")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Send now" })).toBeInTheDocument();
  });

  it("says outreach is not set up where the deployment has no mail service", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ offered: false, providers: [], connection: null, bookingLinkOffered: false });
    renderPage();

    expect(
      await screen.findByText("Outreach email isn't switched on for your workspace. Contact Uncava support to turn it on."),
    ).toBeInTheDocument();
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
    renderPage();

    expect(await screen.findByText("SCHEDULED")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(conversationApi.cancelMessage).toHaveBeenCalledWith("p1", "m7"));
  });

  it("marks the reply task handled and moves on to the next person", async () => {
    vi.mocked(tasksApi.updateTask).mockResolvedValue({ ...replyTask(), status: "done" });
    renderPage();
    await screen.findByRole("heading", { name: "Fatima Al Mazrouei" });

    await userEvent.click(await screen.findByRole("button", { name: "Mark handled" }));

    await waitFor(() => expect(tasksApi.updateTask).toHaveBeenCalledWith("t1", { status: "done" }));
    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent("candidate=c2"));
  });

  it("says why when the queue is refused, and counts nothing", async () => {
    vi.mocked(conversationApi.listConversations).mockRejectedValue(
      Object.assign(new Error("Forbidden"), { problem: { code: "ACCESS_DENIED", status: 403 } }),
    );
    renderPage();

    const queue = await screen.findByRole("region", { name: "Action queue" });
    expect(await within(queue).findByRole("alert")).toBeInTheDocument();
    expect(within(queue).queryByText(/open$/)).not.toBeInTheDocument();
  });
});
