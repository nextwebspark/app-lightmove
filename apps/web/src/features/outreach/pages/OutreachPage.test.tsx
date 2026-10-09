import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as mailboxApi from "../api/mailboxApi";
import type { Mailbox } from "../api/mailboxApi";
import * as runApi from "../api/runApi";
import type { OutreachOverview, OutreachRun } from "../api/runApi";
import * as sequenceApi from "../api/sequenceApi";
import { OutreachPage } from "./OutreachPage";

const auth = vi.hoisted(() => ({ roles: ["MEMBER"] as string[] }));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", workspace: { id: "w1", roles: auth.roles } } }),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
  startMailboxConnect: vi.fn(),
  sendMailboxTest: vi.fn(),
  disconnectMailbox: vi.fn(),
}));

vi.mock("../api/sequenceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sequenceApi")>()),
  getSequences: vi.fn(),
}));

vi.mock("../api/runApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/runApi")>()),
  getOutreachPeople: vi.fn(),
  stopRun: vi.fn(),
}));

vi.mock("../../candidates/components/CandidateDrawerById", () => ({
  CandidateDrawerById: ({ candidateId }: { candidateId: string | null }) =>
    candidateId ? <div role="dialog" aria-label={`Drawer for ${candidateId}`} /> : null,
}));

const PROJECT = { id: "p1", positionTitle: "Group CFO" };

const NOBODY: OutreachOverview = {
  counts: { enrolled: 0, emailsSent: 0, reached: 0, replied: 0, inFlight: 0, bounced: 0, stopped: 0, booked: 0 },
  nextSendAt: null,
  people: [],
};

function run(overrides: Partial<OutreachRun>): OutreachRun {
  return {
    id: "r1",
    candidateId: "c1",
    personId: "person-1",
    fullName: "Omar Farouk",
    title: "CFO",
    companyName: "Gulf Ports",
    candidateStatus: "contacted",
    sequenceId: "s1",
    sequenceName: "CFO — first approach",
    stepCount: 3,
    sentCount: 2,
    nextSendAt: "2026-10-09T05:00:00Z",
    lastSentAt: "2026-10-05T05:00:00Z",
    status: "ACTIVE",
    stopReason: null,
    endedAt: null,
    senderUserId: "u1",
    senderName: "Yara Haddad",
    bookedViaLink: false,
    ...overrides,
  };
}

const WITH_PEOPLE: OutreachOverview = {
  counts: { enrolled: 3, emailsSent: 4, reached: 3, replied: 1, inFlight: 1, bounced: 0, stopped: 1, booked: 0 },
  nextSendAt: "2026-10-09T05:00:00Z",
  people: [
    run({}),
    run({ id: "r2", candidateId: "c2", fullName: "Fatima Al Mazrouei", status: "REPLIED", sentCount: 1,
      nextSendAt: null, endedAt: "2026-10-06T10:20:00Z" }),
    run({ id: "r3", candidateId: "c3", fullName: "James Whitfield", status: "STOPPED", stopReason: "DO_NOT_CONTACT",
      sentCount: 1, nextSendAt: null }),
  ],
};

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
  },
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project: PROJECT }} />}>
              <Route path="*" element={<OutreachPage />} />
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
    auth.roles = ["MEMBER"];
    vi.mocked(sequenceApi.getSequences).mockResolvedValue([]);
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(NOBODY);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("offers each mailbox host the deployment lists, by the name people know it by", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    renderPage();

    expect(await screen.findByRole("button", { name: "Connect Gmail" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Outlook" })).toBeInTheDocument();
  });

  it("opens the popup in the click, then points it at the consent screen the server names", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    const popup = { location: { href: "about:blank" }, closed: false, close: vi.fn() };
    const open = vi.fn(() => popup);
    vi.stubGlobal("open", open);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Connect Gmail" }));

    expect(open).toHaveBeenCalledWith("about:blank", "_blank", expect.any(String));
    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
    await waitFor(() => expect(popup.location.href).toBe("https://mail.example/connect?state=s"));
  });

  it("shows the connected address and sends a test from it", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(mailboxApi.sendMailboxTest).mockResolvedValue(undefined);
    renderPage();

    expect(await screen.findByText("yara@firm.example")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Connect Gmail" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send a test email" }));

    expect(await screen.findByText("Test email sent. Check your inbox.")).toBeInTheDocument();
  });

  it("says how many live sequences a disconnect stops before it disconnects", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, liveSequences: 3, livePeople: 41 },
    });
    vi.mocked(mailboxApi.disconnectMailbox).mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Disconnect" }));
    const dialog = await screen.findByRole("dialog", { name: "Disconnect yara@firm.example?" });
    expect(dialog).toHaveTextContent("3 live sequences (41 people) will stop sending.");
    expect(mailboxApi.disconnectMailbox).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Disconnect" }));
    await waitFor(() => expect(mailboxApi.disconnectMailbox).toHaveBeenCalled());
  });

  it("leaves the count out when nothing is sending", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Disconnect" }));
    const dialog = await screen.findByRole("dialog", { name: "Disconnect yara@firm.example?" });
    expect(dialog).not.toHaveTextContent("will stop sending");
    expect(dialog).toHaveTextContent("You can reconnect later");
  });

  it("asks for a reconnect when the provider withdrew access", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, status: "ERROR" },
    });
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Gmail disconnected.");
    expect(screen.getByRole("button", { name: "Reconnect" })).toBeInTheDocument();
  });

  it("offers a Nylas mailbox the move to Uncava's own connection, saying which runs it stops", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, movesOffNylas: true, runsStoppedByMove: 2 },
    });
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    vi.stubGlobal("open", vi.fn(() => ({ location: { href: "about:blank" }, closed: false, close: vi.fn() })));
    renderPage();

    expect(await screen.findByText("Reconnect to move off Nylas.")).toBeInTheDocument();
    expect(screen.getByText(/2 running sequences stop at their next email/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reconnect" }));

    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
  });

  it("says outreach is not set up where the deployment has no mail service", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ offered: false, providers: [], connection: null, bookingLinkOffered: false });
    renderPage();

    expect(await screen.findByText("Outreach email is not set up on this deployment.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Connect/ })).not.toBeInTheDocument();
  });

  it("never asks for a mailbox on a client representative's behalf", () => {
    auth.roles = ["CLIENT"];
    renderPage();

    expect(screen.getByText("No outreach on this position yet")).toBeInTheDocument();
    expect(mailboxApi.getMailbox).not.toHaveBeenCalled();
  });

  it("counts the position's outreach and lists each run, narrowed by the filter chips", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(WITH_PEOPLE);
    renderPage();

    const table = await screen.findByRole("table", { name: "People in outreach" });
    expect(screen.getByText("Emails sent").nextSibling).toHaveTextContent("4");
    expect(screen.getByText("33% of people reached")).toBeInTheDocument();
    expect(table).toHaveTextContent("Omar Farouk");
    expect(table).toHaveTextContent("In sequence");
    expect(table).toHaveTextContent("Marked do not contact");
    expect(screen.getByTitle("2 of 3 sent")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /^Replied/ }));
    expect(table).toHaveTextContent("Fatima Al Mazrouei");
    expect(table).not.toHaveTextContent("Omar Farouk");
  });

  it("stops a live run from its row, and offers Set status on a reply nobody has recorded", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(WITH_PEOPLE);
    vi.mocked(runApi.stopRun).mockResolvedValue(undefined);
    renderPage();

    const stops = await screen.findAllByRole("button", { name: "Stop" });
    expect(stops).toHaveLength(1);
    await userEvent.click(stops[0]);
    const dialog = await screen.findByRole("dialog", { name: "Stop Omar Farouk's sequence?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Stop sequence" }));
    await waitFor(() => expect(runApi.stopRun).toHaveBeenCalledWith("p1", "r1"));
    expect(await screen.findByText("Stopped. Nothing more goes to Omar Farouk.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Set status" }));
    expect(screen.getByRole("dialog", { name: "Drawer for c2" })).toBeInTheDocument();
  });

  it("shows no counts or table before anyone is on a sequence", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    renderPage();

    await waitFor(() => expect(runApi.getOutreachPeople).toHaveBeenCalled());
    expect(await screen.findByText("No outreach on this position yet")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "People in outreach" })).not.toBeInTheDocument();
  });
});
