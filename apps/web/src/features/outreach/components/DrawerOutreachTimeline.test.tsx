import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import type { PersonNote } from "../../candidates/api/types";
import type { ActivityExecutive } from "../../candidates/lib/activityPresentation";
import * as conversationApi from "../api/conversationApi";
import type { Conversation, ConversationEvent } from "../api/conversationApi";
import type { Meeting } from "../api/meetingApi";
import { linesOf, matches, type TimelineKind } from "../lib/drawerTimeline";
import { DrawerOutreachTimeline } from "./DrawerOutreachTimeline";

vi.mock("../api/conversationApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/conversationApi")>()),
  getConversation: vi.fn(),
}));
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: { id: "u1" } }) }));
vi.mock("../../tasks/lib/usePositionTeam", () => ({ usePositionTeam: () => ({ teammates: [] }) }));

const aNote = (id: string, kind: PersonNote["kind"], body: string, createdAt: string): PersonNote => ({
  id,
  kind,
  body,
  format: "plain",
  pinned: false,
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  authorUserId: "u1",
  authorName: "Layla Nasser",
  authorAvatarUrl: null,
  createdAt,
  editedAt: null,
  editedByName: null,
  editable: true,
});

const noteEvent = (note: PersonNote): ConversationEvent => ({ type: "NOTE", at: note.createdAt, note });

const emailEvent: ConversationEvent = {
  type: "MESSAGE",
  at: "2026-11-03T09:02:00Z",
  message: {
    id: "m1",
    direction: "OUTBOUND",
    channel: "EMAIL",
    kind: "SEQUENCE",
    status: "SENT",
    subject: "Opportunity in Riyadh",
    text: "Hi Sarah, I lead a CFO search.",
    format: "PLAIN",
    toAddress: "sarah@gulfholdings.example",
    senderUserId: "u2",
    senderName: "Omar Haddad",
    senderAvatarUrl: "/api/v1/avatars/omar",
    sequenceName: "CFO approach – Gulf",
    stepNumber: 1,
    stepCount: 3,
    attachments: [],
  },
} as unknown as ConversationEvent;

const replyEvent: ConversationEvent = {
  type: "MESSAGE",
  at: "2026-11-04T08:30:00Z",
  message: {
    id: "m2",
    direction: "INBOUND",
    channel: "EMAIL",
    kind: "REPLY",
    status: "RECEIVED",
    subject: "Re: Opportunity in Riyadh",
    text: "Happy to talk on Thursday.",
    format: "TEXT",
    fromAddress: "sarah@gulfholdings.example",
    senderUserId: "u2",
    senderName: "Omar Haddad",
    senderAvatarUrl: null,
    attachments: [],
  },
} as unknown as ConversationEvent;

const statusEvent = {
  type: "ACTIVITY",
  at: "2026-11-02T10:00:00Z",
  activity: { id: 7, kind: "STATUS_CHANGED", actorName: "Omar Haddad", details: {} },
} as unknown as ConversationEvent;

const EXECUTIVE: ActivityExecutive = {
  fullName: "Sarah Khan",
  face: () => <img alt="Sarah Khan" src="/sarah.jpg" />,
};

const screeningCall: Meeting = {
  id: "mt1",
  title: "Screening call",
  startsAt: "2026-10-28T15:00:00Z",
  endsAt: "2026-10-28T15:32:00Z",
  ownerUserId: "u1",
  ownerName: "Omar Haddad",
  ownerAvatarUrl: null,
  joinUrl: null,
  videoProvider: null,
  viaLink: false,
  bookedInUncava: true,
  editable: false,
};

const callNote = aNote("n-call", "call", "Busy with quarter close.", "2026-10-22T10:45:00Z");
const linkedinNote = aNote("n-li", "linkedin", "Connection request accepted.", "2026-10-18T16:10:00Z");
const plainNote = aNote("n-plain", "general", "Keep the chair out of it.", "2026-10-30T09:00:00Z");
const meetingNote = aNote("n-meet", "meeting", "Open to a move in Q2.", "2026-10-28T16:00:00Z");

const EVENTS = [emailEvent, noteEvent(plainNote), noteEvent(meetingNote), noteEvent(callNote), noteEvent(linkedinNote)];
const PAST = { meetings: [screeningCall], noteIdOf: () => "n-meet" };

describe("linesOf", () => {
  it("leaves plain notes to the Notes tab, folds the Meeting note into its call, and orders newest first", () => {
    const lines = linesOf(EVENTS, PAST);

    expect(lines.map((line) => line.key)).toEqual(["message:m1", "call:mt1", "note:n-call", "note:n-li"]);
    const call = lines.find((line) => line.kind === "call");
    expect(call?.kind === "call" && call.recordedNote?.id).toBe("n-meet");
  });

  it("leaves status and run changes to the Activity tab", () => {
    const lines = linesOf([...EVENTS, statusEvent], PAST);

    expect(lines.map((line) => line.key)).toEqual(["message:m1", "call:mt1", "note:n-call", "note:n-li"]);
  });

  it("narrows by kind", () => {
    const lines = linesOf(EVENTS, PAST);
    const keysOf = (...kinds: TimelineKind[]) =>
      lines.filter((line) => matches(line, new Set(kinds))).map((line) => line.key);

    expect(keysOf("email")).toEqual(["message:m1"]);
    expect(keysOf("calls")).toEqual(["note:n-call"]);
    expect(keysOf("linkedin")).toEqual(["note:n-li"]);
    expect(keysOf("meetings")).toEqual(["call:mt1"]);
    expect(new Set(keysOf("calls", "linkedin"))).toEqual(new Set(["note:n-call", "note:n-li"]));
    expect(keysOf()).toHaveLength(lines.length);
  });

  it("puts a meeting's notes written by hand inside its call, beside the Notetaker's", () => {
    const handNote = { ...aNote("n-hand", "meeting", "Met at the conference.", "2026-10-29T08:00:00Z"), meetingId: "mt1" };
    const lines = linesOf([...EVENTS, noteEvent(handNote)], PAST);

    const call = lines.find((line) => line.kind === "call");
    expect(call?.kind === "call" && call.loggedNotes.map((note) => note.id)).toEqual(["n-hand"]);
    expect(lines.map((line) => line.key)).not.toContain("note:n-hand");
  });

  it("puts a meeting's notes about a call known only from its recording inside it", () => {
    const recordedOnly = { ...screeningCall, id: "recording:r1" };
    const aboutIt = {
      ...aNote("n-rec", "meeting", "She asked about the board.", "2026-10-29T08:00:00Z"),
      recordingId: "r1",
    };
    const lines = linesOf([noteEvent(aboutIt)], { meetings: [recordedOnly], noteIdOf: () => null });

    const call = lines.find((line) => line.kind === "call");
    expect(call?.kind === "call" && call.loggedNotes.map((note) => note.id)).toEqual(["n-rec"]);
    expect(lines.map((line) => line.key)).toEqual(["call:recording:r1"]);
  });

  it("orders a log by when it happened, not when it was typed", () => {
    const backdated = { ...aNote("n-old", "call", "Spoke last month.", "2026-11-04T08:00:00Z"), occurredAt: "2026-09-01T09:00:00Z" };
    const lines = linesOf([...EVENTS, { type: "NOTE", at: backdated.occurredAt, note: backdated }], PAST);

    expect(lines.at(-1)?.key).toBe("note:n-old");
  });

  it("keeps a Meeting note standing alone when no call it came from is listed", () => {
    const lines = linesOf(EVENTS, { meetings: [], noteIdOf: () => null });

    expect(lines.map((line) => line.key)).toContain("note:n-meet");
  });
});

describe("DrawerOutreachTimeline", () => {
  beforeEach(() => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue({
      events: EVENTS,
      truncated: false,
    } as unknown as Conversation);
  });

  function renderTimeline() {
    return render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <DrawerOutreachTimeline
            projectId="p1"
            candidateId="c1"
            firstName="Sarah"
            executive={EXECUTIVE}
            past={{ ...PAST, menuOf: () => null }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );
  }

  it("draws the emails, the logged calls and LinkedIn messages, and the call with its meeting notes", async () => {
    renderTimeline();

    expect(await screen.findByRole("article", { name: "Opportunity in Riyadh" })).toBeInTheDocument();
    expect(screen.getByText("Busy with quarter close.")).toBeInTheDocument();
    expect(screen.getByText("Connection request accepted.")).toBeInTheDocument();
    const call = screen.getByRole("article", { name: "Screening call" });
    // A recorded call says so beside its title, apart from a calendar entry nobody recorded.
    expect(within(call).getByText("Recorded")).toBeInTheDocument();
    expect(within(call).getByText(/Meeting notes · written by the Notetaker/)).toBeInTheDocument();
    expect(within(call).getByText("Open to a move in Q2.")).toBeInTheDocument();
    expect(screen.queryByText("Keep the chair out of it.")).not.toBeInTheDocument();
  });

  it("leads each card with its kind's mark and the face of whoever did it", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue({
      events: [replyEvent, ...EVENTS],
      truncated: false,
    } as unknown as Conversation);
    const { container } = renderTimeline();

    const email = await screen.findByRole("article", { name: "Opportunity in Riyadh" });
    expect(within(email).getByRole("img", { name: "Omar Haddad" })).toHaveAttribute("src", "/api/v1/avatars/omar");
    expect(within(email).getByText("→ sarah@gulfholdings.example")).toBeInTheDocument();
    const reply = screen.getByRole("article", { name: "Sarah replied" });
    expect(within(reply).getByRole("img", { name: "Sarah Khan" })).toBeInTheDocument();
    const linkedin = screen.getByText("Connection request accepted.").closest("li")!;
    expect(within(linkedin).getByText("LinkedIn message")).toBeInTheDocument();
    expect(within(linkedin).getByText("Layla Nasser")).toBeInTheDocument();
    expect(linkedin.querySelector('img[src="/brand/linkedin-in-bug-black.png"]')).not.toBeNull();
    const call = screen.getByRole("article", { name: "Screening call" });
    expect(within(call).getByText("Omar Haddad")).toBeInTheDocument();
    expect(container.querySelector(".w-px")).toBeNull();
  });

  it("names the call's platform, and gives a call known only from its recording the face of who sent the Notetaker", async () => {
    const recordedOnly: Meeting = { ...screeningCall, ownerUserId: "", ownerName: null, videoProvider: "Google Meet" };
    vi.mocked(conversationApi.getConversation).mockResolvedValue({ events: EVENTS, truncated: false } as unknown as Conversation);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <DrawerOutreachTimeline
            projectId="p1"
            candidateId="c1"
            firstName="Sarah"
            executive={EXECUTIVE}
            past={{ meetings: [recordedOnly], noteIdOf: () => "n-meet", menuOf: () => null }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );

    const call = await screen.findByRole("article", { name: "Screening call" });
    expect(within(call).getByText(/^Google Meet · /)).toBeInTheDocument();
    expect(within(call).getByText("Layla Nasser")).toBeInTheDocument();
  });

  it("opens on the latest five, and See more reveals the rest", async () => {
    const many = Array.from({ length: 8 }, (_, index) =>
      noteEvent(aNote(`n-${index}`, "call", `Call number ${index}.`, `2026-10-0${index + 1}T10:00:00Z`)),
    );
    vi.mocked(conversationApi.getConversation).mockResolvedValue({ events: many, truncated: false } as unknown as Conversation);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <DrawerOutreachTimeline
            projectId="p1"
            candidateId="c1"
            firstName="Sarah"
            executive={EXECUTIVE}
            past={{ meetings: [], noteIdOf: () => null, menuOf: () => null }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Call number 7.")).toBeInTheDocument();
    expect(screen.queryByText("Call number 2.")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "See more (3)" }));
    expect(screen.getByText("Call number 0.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show less" }));
    expect(screen.queryByText("Call number 0.")).not.toBeInTheDocument();
  });

  it("opens as far as a linked note on a call further down, and Show less still folds it", async () => {
    const recent = Array.from({ length: 6 }, (_, index) =>
      noteEvent(aNote(`n-${index}`, "call", `Call number ${index}.`, `2026-11-0${index + 1}T10:00:00Z`)),
    );
    const second = { ...aNote("n-hand-2", "meeting", "Second write-up.", "2026-10-29T09:00:00Z"), meetingId: "mt1" };
    const first = { ...aNote("n-hand-1", "meeting", "First write-up.", "2026-10-28T18:00:00Z"), meetingId: "mt1" };
    vi.mocked(conversationApi.getConversation).mockResolvedValue({
      events: [...recent, noteEvent(second), noteEvent(first)],
      truncated: false,
    } as unknown as Conversation);
    Element.prototype.scrollIntoView = vi.fn();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <DrawerOutreachTimeline
            projectId="p1"
            candidateId="c1"
            firstName="Sarah"
            executive={EXECUTIVE}
            past={{ meetings: [screeningCall], noteIdOf: () => null, menuOf: () => null }}
            focus={{ noteId: "n-hand-2", at: 1 }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Second write-up.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show less" }));
    expect(screen.queryByText("Second write-up.")).not.toBeInTheDocument();
  });

  it("narrows to the kinds ticked in the filter, and shows everything again once cleared", async () => {
    renderTimeline();
    await screen.findByRole("article", { name: "Opportunity in Riyadh" });

    await userEvent.click(screen.getByRole("button", { name: "Filter by kind" }));
    await userEvent.click(screen.getByRole("menuitemcheckbox", { name: /Calls/ }));

    expect(screen.getByText("Busy with quarter close.")).toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "Opportunity in Riyadh" })).not.toBeInTheDocument();
    expect(screen.queryByText("Connection request accepted.")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("menuitemcheckbox", { name: /LinkedIn/ }));
    expect(screen.getByText("Busy with quarter close.")).toBeInTheDocument();
    expect(screen.getByText("Connection request accepted.")).toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "Opportunity in Riyadh" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(screen.getByRole("article", { name: "Opportunity in Riyadh" })).toBeInTheDocument();
  });

  it("says nothing has passed yet on an empty history", async () => {
    vi.mocked(conversationApi.getConversation).mockResolvedValue({ events: [], truncated: false } as unknown as Conversation);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <DrawerOutreachTimeline
            projectId="p1"
            candidateId="c1"
            firstName="Sarah"
            executive={EXECUTIVE}
            past={{ meetings: [], noteIdOf: () => null, menuOf: () => null }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("No contact with Sarah yet")).toBeInTheDocument();
  });
});
