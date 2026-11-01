import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { typeInto } from "../../../test/noteEditor";
import * as documentsApi from "../../candidates/api/documentsApi";
import * as signaturesApi from "../../signatures/api/signaturesApi";
import type { EmailSignature } from "../../signatures/api/signaturesApi";
import * as conversationApi from "../api/conversationApi";
import type { Conversation } from "../api/conversationApi";
import type { MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { ReplyComposer } from "./ReplyComposer";

vi.mock("../api/conversationApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/conversationApi")>()),
  sendMessage: vi.fn(),
}));

vi.mock("../../signatures/api/signaturesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../signatures/api/signaturesApi")>()),
  listSignatures: vi.fn(),
}));

vi.mock("../../candidates/api/documentsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/documentsApi")>()),
  listDocuments: vi.fn(),
}));

const MAILBOX: MailboxState = {
  kind: "ready",
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
const CONNECTOR: MailboxConnect = { connectingProvider: null, connect: vi.fn() };

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    executive: {
      candidateId: "c1",
      personId: "person-1",
      fullName: "Fatima Al Mazrouei",
      title: "CFO",
      companyName: "Gulf Ports",
      triageCompanyId: null,
      status: "contacted",
      statusLabel: "Contacted",
      statusColour: "accent",
      outOfProcess: false,
      pipelineStage: { key: "contacted", label: "Contacted", colour: "accent", position: 2 },
      addedAt: "2026-09-20T05:00:00Z",
      statusChangedAt: "2026-10-01T05:00:00Z",
      nextAction: null,
      clientVisible: false,
    },
    openTasks: [],
    currentRun: { run: null, steps: [] },
    keyDates: { addedAt: null, firstContactedAt: null, repliedAt: null, lastActivityAt: null },
    contact: {
      emails: [{ address: "fatima@gulfports.example", kind: "work", verified: true } as never],
      phones: [],
      locationCity: null,
      locationCountry: null,
      doNotContact: false,
    },
    composer: { replySubject: null, replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } },
    events: [],
    truncated: false,
    ...overrides,
  };
}

const SOURCES = {
  name: "Yara Haddad",
  title: null,
  phone: null,
  linkedinUrl: null,
  email: "yara@firm.example",
  company: "Firm",
  website: null,
  photoUrl: null,
  logoAvailable: false,
  bookingLinkOffered: false,
};

function aSignature(id: string, name: string, isDefault: boolean): EmailSignature {
  return {
    id,
    name,
    layout: "MINIMAL",
    fields: { name: null, title: null, phone: null, linkedinUrl: null, company: null, website: null, address: null },
    showPhoto: false,
    showLogo: false,
    showBookingLink: false,
    customBody: null,
    disclaimer: null,
    isDefault,
    updatedAt: "2026-11-01T10:00:00Z",
    html: `<div>${name}</div>`,
    text: `-- \n${name}`,
  };
}

function wrapper({ children }: { children: ReactNode }) {
  return (
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>{children}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
}

function composerFor(held: Conversation) {
  return <ReplyComposer projectId="p1" conversation={held} mailbox={MAILBOX} connector={CONNECTOR} isArchived={false} />;
}

describe("ReplyComposer", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({ signatures: [], sources: SOURCES, limit: 10 });
    vi.mocked(conversationApi.sendMessage).mockResolvedValue({
      id: "m1",
      status: "SCHEDULED",
      sendAt: "2026-10-22T06:00:00Z",
      subject: "Hello",
      inThread: false,
    });
    vi.mocked(documentsApi.listDocuments).mockResolvedValue([
      {
        id: "d1",
        category: "cv",
        title: "Fatima CV",
        primaryCv: true,
        projectId: "p1",
        projectTitle: "Group CFO",
        createdBy: "u1",
        createdByName: "Yara",
        createdAt: "2026-10-01T09:00:00Z",
        updatedAt: "2026-10-01T09:00:00Z",
        removable: true,
        versions: [
          {
            id: "v2",
            versionNo: 2,
            fileName: "Fatima_CV_2026.pdf",
            contentType: "application/pdf",
            sizeBytes: 120_000,
            uploadedBy: "u1",
            uploadedByName: "Yara",
            uploadedAt: "2026-10-01T09:00:00Z",
            previewable: true,
            removable: true,
          },
        ],
      },
    ]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function writeFirstEmail() {
    await userEvent.type(screen.getByRole("textbox", { name: "Subject" }), "A conversation");
    await typeInto(await screen.findByRole("textbox", { name: "Message" }), "Hello");
  }

  it("carries the default signature, ticked, and another or none as the sender chooses", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [aSignature("s2", "Short reply", false), aSignature("s1", "Formal", true)],
      sources: SOURCES,
      limit: 10,
    });
    render(composerFor(conversation()), { wrapper });
    await writeFirstEmail();

    const include = await screen.findByRole("checkbox", { name: "Include signature" });
    expect(include).toBeChecked();
    expect(screen.getByRole("combobox", { name: "Signature" })).toHaveValue("s1");
    expect(screen.getByTitle("Signature")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Signature" }), "s2");
    await userEvent.click(screen.getByRole("button", { name: "Send now" }));
    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenLastCalledWith("p1", "c1", expect.objectContaining({ signatureId: "s2" }), []),
    );

    await writeFirstEmail();
    await userEvent.click(screen.getByRole("checkbox", { name: "Include signature" }));
    expect(screen.queryByTitle("Signature")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send now" }));
    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenLastCalledWith("p1", "c1", expect.objectContaining({ signatureId: null }), []),
    );
  });

  it("sends none, and points to Settings, for a sender who keeps no signature", async () => {
    render(composerFor(conversation()), { wrapper });
    await writeFirstEmail();

    expect(await screen.findByRole("link", { name: "Add one in Settings" })).toHaveAttribute("href", "/settings/signature");
    await userEvent.click(screen.getByRole("button", { name: "Send now" }));
    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith("p1", "c1", expect.objectContaining({ signatureId: null }), []),
    );
  });

  it("schedules at a day and half hour on the mailbox's own clock, held to 60 days and never a cleared day", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-21T06:00:00Z"));
    render(composerFor(conversation()), { wrapper });
    await writeFirstEmail();

    await userEvent.click(screen.getByRole("button", { name: "Schedule" }));
    const day = screen.getByLabelText("Send on");
    fireEvent.change(day, { target: { value: "" } });
    expect(screen.getByRole("button", { name: "Choose a day and a time" })).toBeDisabled();

    fireEvent.change(day, { target: { value: "2026-12-20" } });
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Send at" }), "23:30");
    expect(screen.getByRole("button", { name: "Pick a time within the next 60 days" })).toBeDisabled();

    fireEvent.change(day, { target: { value: "2026-10-22" } });
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Send at" }), "10:00");
    await userEvent.click(screen.getByRole("button", { name: /^Schedule for / }));

    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ sendAt: "2026-10-22T06:00:00.000Z", subject: "A conversation", replyInThread: false }),
        [],
      ),
    );
  });

  it("writes to an address still on the ledger when the one it showed is removed", async () => {
    const { rerender } = render(composerFor(conversation()), { wrapper });
    await writeFirstEmail();

    rerender(
      composerFor(
        conversation({ contact: { ...conversation().contact, emails: [{ address: "fatima@new.example", kind: "work", verified: false } as never] } }),
      ),
    );
    expect(screen.getByText("fatima@new.example")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send now" }));

    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ toAddress: "fatima@new.example" }),
        [],
      ),
    );
  });

  it("keeps a draft in progress when a reply thread appears, and offers the thread to an empty one", async () => {
    const { rerender } = render(composerFor(conversation()), { wrapper });
    await writeFirstEmail();

    const withThread = conversation({ composer: { replySubject: "Re: Hello", replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } });
    rerender(composerFor(withThread));
    expect(screen.getByText("First email to Fatima")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Subject" })).toHaveValue("A conversation");
  });

  it("turns an empty composer into a reply once a thread appears, and back once it goes", () => {
    const { rerender } = render(composerFor(conversation()), { wrapper });
    expect(screen.getByText("First email to Fatima")).toBeInTheDocument();

    rerender(composerFor(conversation({ composer: { replySubject: "Re: Hello", replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } })));
    const composer = screen.getByRole("region", { name: "Reply" });
    expect(within(composer).getByText("Reply to Fatima")).toBeInTheDocument();

    rerender(composerFor(conversation()));
    expect(screen.getByText("First email to Fatima")).toBeInTheDocument();
  });

  it("says a reply stops the running sequence only where the server says it would", async () => {
    const { rerender } = render(
      composerFor(conversation({ composer: { replySubject: "Re: Hello", replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } })),
      { wrapper },
    );
    expect(screen.queryByText(/Sending stops the running sequence/)).not.toBeInTheDocument();

    rerender(composerFor(conversation({ composer: { replySubject: "Re: Hello", replyStopsRun: true, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } })));
    expect(screen.getByText(/Sending stops the running sequence/)).toBeInTheDocument();
  });

  it("refuses a body past the server's limit before anything is sent", async () => {
    render(composerFor(conversation({ composer: { replySubject: "Re: Hello", replyStopsRun: false, emailSentMovesTo: null, attachmentLimits: { maxFiles: 5, maxFileBytes: 3_000_000, maxTotalBytes: 10_485_760 } } })), {
      wrapper,
    });
    const box = await screen.findByRole("textbox", { name: "Message" });
    await typeInto(box, "x");
    const { editorOf } = await import("../../../test/noteEditor");
    editorOf(box).commands.setContent("y".repeat(10_001));

    expect(await screen.findByRole("alert")).toHaveTextContent("Too long to send.");
    expect(screen.getByRole("button", { name: "Send now" })).toBeDisabled();
  });

  it("sends their document and an uploaded file with the email, and holds a file past the limit back", async () => {
    render(composerFor(conversation()), { wrapper });
    expect(documentsApi.listDocuments).not.toHaveBeenCalled();
    await writeFirstEmail();

    await userEvent.click(screen.getByRole("button", { name: "Attach" }));
    await userEvent.click(await screen.findByRole("button", { name: /Fatima CV/ }));
    const brief = new File(["%PDF-1.7"], "Brief.pdf", { type: "application/pdf" });
    await userEvent.upload(screen.getByLabelText("Attach a file from this computer"), brief);
    const chips = screen.getByRole("list", { name: "Attachments" });
    expect(within(chips).getByText("Fatima_CV_2026.pdf")).toBeInTheDocument();
    expect(within(chips).getByText("Brief.pdf")).toBeInTheDocument();

    const huge = new File(["x"], "Huge.pdf", { type: "application/pdf" });
    Object.defineProperty(huge, "size", { value: 3_000_001 });
    await userEvent.upload(screen.getByLabelText("Attach a file from this computer"), huge);
    expect(screen.getByRole("alert")).toHaveTextContent("Huge.pdf is larger than 2.9 MB");
    expect(screen.getByRole("button", { name: "Send now" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Remove Huge.pdf" }));

    await userEvent.click(screen.getByRole("button", { name: "Send now" }));
    await waitFor(() =>
      expect(conversationApi.sendMessage).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ documentVersionIds: ["v2"] }),
        [brief],
      ),
    );
  });
});
