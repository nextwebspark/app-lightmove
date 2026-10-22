import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import * as conversationApi from "../api/conversationApi";
import type { Conversation } from "../api/conversationApi";
import * as mailboxApi from "../api/mailboxApi";
import type { Mailbox } from "../api/mailboxApi";
import { CandidateOutreachButtons } from "./CandidateOutreachButtons";

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

vi.mock("../api/conversationApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/conversationApi")>()),
  getConversation: vi.fn(),
}));

vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));

const CONNECTED: Mailbox = {
  offered: true,
  providers: ["google"],
  bookingLinkOffered: false,
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
    sentToday: 0,
    scheduledEmails: 0,
  },
};

const FIRST_EMAIL = {
  executive: { candidateId: "c1", personId: "person-1", fullName: "Fatima Al Mazrouei", statusLabel: "Identified" },
  openTasks: [],
  currentRun: { run: null, steps: [] },
  contact: {
    emails: [{ address: "fatima@gulfports.example", kind: "work", verified: true }],
    phones: [],
    locationCity: null,
    locationCountry: null,
    doNotContact: false,
  },
  composer: {
    replySubject: null,
    replyStopsRun: false,
    emailSentMovesTo: null,
    attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 },
  },
  events: [],
  truncated: false,
} as unknown as Conversation;

function renderButtons() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <CandidateOutreachButtons projectId="p1" candidateId="c1" fullName="Fatima Al Mazrouei" />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("CandidateOutreachButtons", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(CONNECTED);
    vi.mocked(conversationApi.getConversation).mockResolvedValue(FIRST_EMAIL);
    vi.mocked(projectsApi.projects).mockResolvedValue([{ id: "p1", archivedAt: null }] as never);
  });

  it("offers an email of the consultant's own beside a sequence, and writes it in place", async () => {
    renderButtons();

    expect(await screen.findByRole("button", { name: "Add to sequence" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Write email" }));

    const dialog = await screen.findByRole("dialog", { name: "Email Fatima Al Mazrouei" });
    expect(await within(dialog).findByText("First email to Fatima")).toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByRole("textbox", { name: "Subject" })).toHaveFocus());
    expect(within(dialog).getByRole("link", { name: "Open their conversation" })).toHaveAttribute(
      "href",
      "/projects/p1/outreach?candidate=c1",
    );
  });

  it("with no mailbox, keeps both quiet and offers the connection instead", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ ...CONNECTED, connection: null });
    renderButtons();

    const writeEmail = await screen.findByRole("button", { name: "Write email" });
    expect(writeEmail).toHaveAttribute("title", "Connect your mailbox first");
    expect(screen.getByRole("button", { name: "Add to sequence" })).toHaveAttribute("title", "Connect your mailbox first");
    await userEvent.click(writeEmail);

    const connect = await screen.findByRole("group", { name: "Connect your mailbox first" });
    expect(within(connect).getByRole("button", { name: "Connect Gmail" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Email Fatima Al Mazrouei" })).not.toBeInTheDocument();
  });

  it("offers neither where outreach email is off", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ ...CONNECTED, offered: false, connection: null });
    renderButtons();

    await waitFor(() => expect(mailboxApi.getMailbox).toHaveBeenCalled());
    await new Promise((settle) => setTimeout(settle, 0));
    expect(screen.queryByRole("button", { name: "Write email" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add to sequence" })).not.toBeInTheDocument();
  });
});
