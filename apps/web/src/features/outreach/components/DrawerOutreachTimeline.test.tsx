import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import type { PersonNote } from "../../candidates/api/types";
import * as conversationApi from "../api/conversationApi";
import type { Conversation, ConversationEvent } from "../api/conversationApi";
import type { Meeting } from "../api/meetingApi";
import { linesOf, matches } from "../lib/drawerTimeline";
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
    sequenceName: "CFO approach – Gulf",
    stepNumber: 1,
    stepCount: 3,
    attachments: [],
  },
} as unknown as ConversationEvent;

const screeningCall: Meeting = {
  id: "mt1",
  title: "Screening call",
  startsAt: "2026-10-28T15:00:00Z",
  endsAt: "2026-10-28T15:32:00Z",
  ownerUserId: "u1",
  ownerName: "Omar Haddad",
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

  it("narrows by kind", () => {
    const lines = linesOf(EVENTS, PAST);
    const keysOf = (filter: Parameters<typeof matches>[1]) =>
      lines.filter((line) => matches(line, filter)).map((line) => line.key);

    expect(keysOf("email")).toEqual(["message:m1"]);
    expect(keysOf("calls")).toEqual(["note:n-call"]);
    expect(keysOf("linkedin")).toEqual(["note:n-li"]);
    expect(keysOf("meetings")).toEqual(["call:mt1"]);
  });

  it("puts a meeting's notes written by hand inside its call, beside the Notetaker's", () => {
    const handNote = { ...aNote("n-hand", "meeting", "Met at the conference.", "2026-10-29T08:00:00Z"), meetingId: "mt1" };
    const lines = linesOf([...EVENTS, noteEvent(handNote)], PAST);

    const call = lines.find((line) => line.kind === "call");
    expect(call?.kind === "call" && call.loggedNotes.map((note) => note.id)).toEqual(["n-hand"]);
    expect(lines.map((line) => line.key)).not.toContain("note:n-hand");
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
    expect(within(call).getByText(/Meeting notes · written by the Notetaker/)).toBeInTheDocument();
    expect(within(call).getByText("Open to a move in Q2.")).toBeInTheDocument();
    expect(screen.queryByText("Keep the chair out of it.")).not.toBeInTheDocument();
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

  it("narrows to one kind from the filter", async () => {
    renderTimeline();
    await screen.findByRole("article", { name: "Opportunity in Riyadh" });

    await userEvent.click(screen.getByRole("radio", { name: "Calls" }));

    expect(screen.getByText("Busy with quarter close.")).toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "Opportunity in Riyadh" })).not.toBeInTheDocument();
    expect(screen.queryByText("Connection request accepted.")).not.toBeInTheDocument();
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
            past={{ meetings: [], noteIdOf: () => null, menuOf: () => null }}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("No contact with Sarah yet")).toBeInTheDocument();
  });
});
