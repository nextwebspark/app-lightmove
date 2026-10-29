import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as conversationApi from "../../../outreach/api/conversationApi";
import type { Conversation, ConversationMessage } from "../../../outreach/api/conversationApi";
import * as meetingApi from "../../../outreach/api/meetingApi";
import * as tasksApi from "../../../tasks/api/tasksApi";
import * as personCrmApi from "../../api/personCrmApi";
import * as poolApi from "../../api/poolApi";
import type { PersonActivityKind, PersonDocument, PersonTimelineEntry } from "../../api/types";
import type { ActivityExecutive, ActivityScope } from "../../lib/activityPresentation";
import { CandidateAvatar } from "../CandidateAvatar";
import type { ActivityDestinations } from "./ActivityDetailDialog";
import { ActivityTimeline } from "./ActivityTimeline";

vi.mock("../../api/personCrmApi", async (importOriginal) => ({
  ...(await importOriginal<typeof personCrmApi>()),
  getPositionHistory: vi.fn(),
  getPersonNotes: vi.fn(),
}));
vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  getPersonHistory: vi.fn(),
}));
vi.mock("../../../outreach/api/conversationApi", async (importOriginal) => ({
  ...(await importOriginal<typeof conversationApi>()),
  getConversation: vi.fn(),
}));
vi.mock("../../../outreach/api/meetingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof meetingApi>()),
  getMeetings: vi.fn(),
}));
vi.mock("../../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof tasksApi>()),
  getCandidateTasks: vi.fn(),
}));

const CANDIDATE = { id: "c1", fullName: "Fatima Al Mazrouei", enrichedAt: null };
const EXECUTIVE: ActivityExecutive = {
  fullName: CANDIDATE.fullName,
  face: (size, className) => <CandidateAvatar projectId="p1" candidate={CANDIDATE} size={size} className={className} />,
};
const ON_POSITION: ActivityScope = { kind: "position", projectId: "p1", candidateId: "c1" };
const NOW = new Date("2026-10-28T11:20:00Z");

const line = (id: number, kind: PersonActivityKind, overrides: Partial<PersonTimelineEntry> = {}): PersonTimelineEntry => ({
  id,
  kind,
  occurredAt: "2026-10-27T09:10:00Z",
  actorUserId: "dana",
  actorName: "Dana Aboud",
  actorAvatarUrl: null,
  personId: "person-1",
  personName: "Fatima Al Mazrouei",
  projectId: "p1",
  projectTitle: "Group CFO",
  details: {},
  noteExcerpt: null,
  noteEditedAt: null,
  noteEditedByName: null,
  changes: [],
  ...overrides,
});

const history = (...entries: PersonTimelineEntry[]) =>
  vi.mocked(personCrmApi.getPositionHistory).mockResolvedValue({ entries, truncated: false });

let destinations: ActivityDestinations;

function renderTimeline(documents: PersonDocument[] | undefined = [], scope: ActivityScope = ON_POSITION) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ActivityTimeline scope={scope} executive={EXECUTIVE} documents={documents} destinations={destinations} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const failure = (status: number) =>
  new ApiRequestError({ code: "INTERNAL_ERROR", detail: "boom", status, correlationId: "x" });

describe("ActivityTimeline", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
    destinations = {
      onShowNote: vi.fn(),
      onOpenTab: vi.fn(),
      onOpenMeetings: vi.fn(),
      onOpenTask: vi.fn(),
      onPreviewDocument: vi.fn(),
    };
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([]);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe("reading the history", () => {
    it("says nothing is recorded yet, once the read has come back empty", async () => {
      history();
      renderTimeline();
      expect(await screen.findByText("Nothing recorded on this position yet.")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Filter by kind" })).not.toBeInTheDocument();
    });

    it("says why it could not read, and reads again on Try again", async () => {
      vi.mocked(personCrmApi.getPositionHistory)
        .mockRejectedValueOnce(failure(500))
        .mockResolvedValueOnce({ entries: [line(1, "NOTE_ADDED")], truncated: false });
      renderTimeline();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("Couldn't load the activity.");
      await userEvent.click(within(alert).getByRole("button", { name: "Try again" }));
      expect(await screen.findByRole("button", { name: /^Dana Aboud wrote a note/ })).toBeInTheDocument();
    });

    it("says when the oldest events are left unread", async () => {
      vi.mocked(personCrmApi.getPositionHistory).mockResolvedValue({ entries: [line(1, "NOTE_ADDED")], truncated: true });
      renderTimeline();
      expect(await screen.findByText(/Showing the latest 1 events\. Older ones are kept/)).toBeInTheDocument();
    });

    it("groups by day, newest first, with the date beside Today and Yesterday", async () => {
      history(
        line(3, "NOTE_ADDED", { occurredAt: "2026-10-28T11:06:00Z" }),
        line(2, "EMAIL_SENT", { occurredAt: "2026-10-27T14:02:00Z" }),
        line(1, "ADDED_TO_POOL", { occurredAt: "2026-09-08T14:40:00Z", details: { door: "EXTENSION" } }),
      );
      renderTimeline();

      const today = await screen.findByRole("region", { name: "Today" });
      expect(within(today).getByText("Wed 28 Oct")).toBeInTheDocument();
      expect(within(today).getByText("14 min ago")).toBeInTheDocument();
      expect(screen.getByRole("region", { name: "Yesterday" })).toBeInTheDocument();
      expect(screen.getByRole("region", { name: /8 Sep/ })).toBeInTheDocument();
      expect(screen.getByText("Start of this position's history")).toBeInTheDocument();
    });
  });

  describe("who and when", () => {
    it("names the executive on their own reply, and Uncava's rule on the status it moved", async () => {
      history(
        line(3, "STATUS_CHANGED", {
          details: { automation: "EMAIL_SENT", from: "identified", to: "contacted", fromLabel: "Identified", toLabel: "Contacted" },
        }),
        line(2, "EMAIL_REPLIED", { actorUserId: null, actorName: null, details: { sequence: "CFO approach" } }),
      );
      renderTimeline();

      const moved = await screen.findByRole("button", { name: /^Automation moved the status/ });
      expect(moved).toHaveTextContent("after Dana Aboud's email");
      expect(within(moved).getByTitle("Automation")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Fatima Al Mazrouei replied to the sequence/ })).toBeInTheDocument();
    });

    it("gives every card the full date and zone on its time", async () => {
      history(line(1, "NOTE_ADDED", { occurredAt: "2026-10-27T14:02:00Z" }));
      renderTimeline();
      await screen.findByRole("button", { name: /wrote a note/ });
      const time = document.querySelector("time[datetime='2026-10-27T14:02:00Z']");
      expect(time).toHaveAttribute("title", expect.stringMatching(/Tue 27 Oct 2026/));
      expect(time).toHaveAttribute("title", expect.stringMatching(/(GMT|UTC)/));
    });
  });

  describe("filters", () => {
    const team = () =>
      history(
        line(6, "NOTE_ADDED", { occurredAt: "2026-10-28T10:00:00Z" }),
        line(5, "EMAIL_SENT", { occurredAt: "2026-10-28T09:00:00Z" }),
        line(4, "EMAIL_SENT", { occurredAt: "2026-10-27T09:00:00Z", actorUserId: "omar", actorName: "Omar Khalil" }),
        line(3, "MEETING_BOOKED", { occurredAt: "2026-10-26T09:00:00Z", actorUserId: "omar", actorName: "Omar Khalil", details: { startsAt: "2026-10-30T11:00:00Z" } }),
      );

    it("narrows by kind, counts what shows, and clears in one press", async () => {
      team();
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: "Filter by kind" }));
      const menu = screen.getByRole("menu", { name: "Show" });
      const emails = within(menu).getByRole("menuitemcheckbox", { name: /Emails/ });
      expect(emails).toHaveTextContent("2");
      await userEvent.click(emails);

      expect(emails).toHaveAttribute("aria-checked", "true");
      expect(screen.getByText("2 of 4")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /wrote a note/ })).not.toBeInTheDocument();
      expect(screen.getAllByRole("button", { name: /sent an email/ })).toHaveLength(2);

      await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
      expect(screen.getByText("4 events")).toBeInTheDocument();
    });

    it("combines several kinds, and Only keeps one", async () => {
      team();
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: "Filter by kind" }));
      const menu = screen.getByRole("menu", { name: "Show" });
      await userEvent.click(within(menu).getByRole("menuitemcheckbox", { name: /Emails/ }));
      await userEvent.click(within(menu).getByRole("menuitemcheckbox", { name: /Meetings/ }));
      expect(screen.getByText("3 of 4")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Filter by kind" })).toHaveTextContent("Emails +1");

      await userEvent.click(within(menu).getByRole("button", { name: "Only Notes" }));
      expect(screen.getByText("1 of 4")).toBeInTheDocument();
      expect(within(menu).getByRole("menuitemcheckbox", { name: /Emails/ })).toHaveAttribute("aria-checked", "false");
    });

    it("narrows by person, its counts following the kind chosen, and an option with nothing to show disabled", async () => {
      team();
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: "Filter by kind" }));
      await userEvent.click(within(screen.getByRole("menu", { name: "Show" })).getByRole("menuitemcheckbox", { name: /Meetings/ }));
      await userEvent.keyboard("{Escape}");

      await userEvent.click(screen.getByRole("button", { name: "Filter by person" }));
      const people = screen.getByRole("menu", { name: "Done by" });
      expect(within(people).getByRole("menuitemcheckbox", { name: /Dana Aboud/ })).toBeDisabled();
      const omar = within(people).getByRole("menuitemcheckbox", { name: /Omar Khalil/ });
      expect(omar).toHaveTextContent("1");
      await userEvent.click(omar);
      expect(screen.getByRole("button", { name: "Filter by person" })).toHaveTextContent("Omar");
      expect(screen.getByText("1 of 4")).toBeInTheDocument();
    });

    it("closes a menu on Escape and on a click outside it, giving focus back to its button", async () => {
      team();
      renderTimeline();
      const button = await screen.findByRole("button", { name: "Filter by kind" });
      await userEvent.click(button);
      await userEvent.keyboard("{Escape}");
      expect(screen.queryByRole("menu")).not.toBeInTheDocument();
      expect(button).toHaveFocus();

      await userEvent.click(button);
      await userEvent.click(screen.getByText("4 events"));
      expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("moves through a menu with the arrow keys", async () => {
      team();
      renderTimeline();
      const button = await screen.findByRole("button", { name: "Filter by kind" });
      button.focus();
      await userEvent.keyboard("{ArrowDown}");
      const items = within(await screen.findByRole("menu", { name: "Show" })).getAllByRole("menuitemcheckbox");
      await vi.waitFor(() => expect(items[0]).toHaveFocus());
      await userEvent.keyboard("{ArrowDown}");
      expect(items[1]).toHaveFocus();
      await userEvent.keyboard("{ArrowUp}{ArrowUp}");
      expect(items[items.length - 1]).toHaveFocus();
    });

    it("shows a filter with a single option as a plain label, never a menu of one", async () => {
      history(line(2, "TAGGED", { details: { tag: "Referral" } }), line(1, "NOTE_ADDED"));
      renderTimeline();
      await screen.findByRole("button", { name: "Filter by kind" });
      expect(screen.queryByRole("button", { name: "Filter by person" })).not.toBeInTheDocument();
      expect(screen.getByTitle("Only Dana Aboud so far")).toHaveTextContent("Dana Aboud");
    });
  });

  describe("folding", () => {
    it("folds three quick changes by one person into a line that opens in place", async () => {
      history(
        line(3, "TAGGED", { occurredAt: "2026-10-27T09:14:00Z", details: { tag: "Open to relocate" } }),
        line(2, "OWNER_CHANGED", { occurredAt: "2026-10-27T09:12:00Z", details: { ownerUserId: "y", owner: "Yara Haddad" } }),
        line(1, "CONTACTS_EDITED", { occurredAt: "2026-10-27T09:10:00Z", details: { emails: "2", phones: "1" } }),
      );
      renderTimeline();

      const fold = await screen.findByRole("button", { name: /Dana Aboud made 3 changes/ });
      expect(fold).toHaveAttribute("aria-expanded", "false");
      expect(screen.queryByRole("button", { name: /made Yara Haddad the owner/ })).not.toBeInTheDocument();
      await userEvent.click(fold);
      expect(fold).toHaveAttribute("aria-expanded", "true");
      expect(screen.getByRole("button", { name: /made Yara Haddad the owner/ })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /edited the contacts, 2 emails, 1 phone on file/ })).toBeInTheDocument();
    });
  });

  describe("notes, call logs, meeting notes and logged emails", () => {
    const callNote = {
      id: "n1",
      kind: "call" as const,
      body: "Open to a group CFO move.\n\n- Notice is three months\n- Wants Abu Dhabi",
      format: "markdown" as const,
      pinned: false,
      projectId: "p1",
      projectTitle: "Group CFO",
      authorUserId: "dana",
      authorName: "Dana Aboud",
      authorAvatarUrl: null,
      createdAt: "2026-10-27T09:10:00Z",
      editedAt: "2026-10-27T10:00:00Z",
      editedByName: "Omar Khalil",
      editable: true,
    };

    it("previews a note as it was written, its bullets kept, never as flattened words", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([callNote]);
      history(line(1, "NOTE_ADDED", { details: { noteId: "n1", kind: "CALL" }, noteExcerpt: "Open to a group CFO move. - Notice is three months" }));
      renderTimeline();
      const card = await screen.findByRole("button", { name: /^Dana Aboud logged a call/ });
      expect(await within(card).findByText("Notice is three months")).toBeInTheDocument();
      expect(within(card).getByText("Wants Abu Dhabi")).toBeInTheDocument();
      expect(within(card).queryByText(/- Notice is three months/)).not.toBeInTheDocument();
    });

    it("marks a note edited since it was written, from the note it shows or the server's line", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([
        callNote,
        { ...callNote, id: "n2", kind: "general", body: "Prefers a listed company.", format: "plain", editedAt: null, editedByName: null },
      ]);
      history(
        line(3, "NOTE_ADDED", { details: { noteId: "n1", kind: "CALL" }, noteExcerpt: "Open" }),
        line(2, "NOTE_ADDED", { details: { noteId: "n2", kind: "GENERAL" }, noteExcerpt: "Prefers" }),
        line(1, "NOTE_ADDED", {
          details: { noteId: "elsewhere", kind: "MEETING" },
          noteExcerpt: "Intro call summary",
          noteEditedAt: "2026-10-27T11:00:00Z",
          noteEditedByName: "Omar Khalil",
        }),
      );
      renderTimeline();
      const call = await screen.findByRole("button", { name: /^Dana Aboud logged a call, edited/ });
      expect(within(call).getByText("Edited")).toHaveAttribute("title", expect.stringMatching(/^Edited by Omar Khalil/));
      const meeting = screen.getByRole("button", { name: /^Dana Aboud logged a meeting, edited/ });
      expect(within(meeting).getByText("Edited")).toBeInTheDocument();
      const note = await screen.findByRole("button", { name: /^Dana Aboud wrote a note/ });
      expect(within(note).queryByText("Edited")).not.toBeInTheDocument();
    });

    it("trusts the server's line when the cached note has not caught up with an edit", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([{ ...callNote, editedAt: null, editedByName: null }]);
      history(
        line(1, "NOTE_ADDED", {
          details: { noteId: "n1", kind: "CALL" },
          noteExcerpt: "Open",
          noteEditedAt: "2026-10-27T10:00:00Z",
          noteEditedByName: "Omar Khalil",
        }),
      );
      renderTimeline();
      const call = await screen.findByRole("button", { name: /^Dana Aboud logged a call, edited/ });
      expect(await within(call).findByText("Notice is three months")).toBeInTheDocument();
      expect(within(call).getByText("Edited")).toHaveAttribute("title", expect.stringMatching(/^Edited by Omar Khalil/));
    });

    it("falls back to the server's opening words when the notes cannot be read, or no longer hold it", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockRejectedValue(failure(500));
      history(
        line(2, "NOTE_ADDED", { details: { noteId: "n1", kind: "MEETING" }, noteExcerpt: "Intro call summary" }),
        line(1, "NOTE_ADDED", { occurredAt: "2026-10-26T09:00:00Z", details: { noteId: "gone", kind: "EMAIL" }, noteExcerpt: null }),
      );
      renderTimeline();
      const meeting = await screen.findByRole("button", { name: /^Dana Aboud logged a meeting/ });
      expect(within(meeting).getByText("Intro call summary")).toBeInTheDocument();
      const email = screen.getByRole("button", { name: /^Dana Aboud logged an email/ });
      expect(within(email).getByText("This note was deleted.")).toBeInTheDocument();
    });

    it("opens each kind under its own title, with who last edited it", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([
        callNote,
        { ...callNote, id: "n2", kind: "meeting", body: "Intro call summary", format: "plain", editedAt: null, editedByName: null },
        { ...callNote, id: "n3", kind: "email", body: "Sent the overview.", format: "plain", editedAt: null, editedByName: null },
        { ...callNote, id: "n4", kind: "general", body: "Prefers a listed company.", format: "plain", projectId: null, editedAt: null, editedByName: null },
      ]);
      history(
        line(4, "NOTE_ADDED", { details: { noteId: "n1", kind: "CALL" }, noteExcerpt: "Open" }),
        line(3, "NOTE_ADDED", { details: { noteId: "n2", kind: "MEETING" }, noteExcerpt: "Intro" }),
        line(2, "NOTE_ADDED", { details: { noteId: "n3", kind: "EMAIL" }, noteExcerpt: "Sent" }),
        line(1, "NOTE_ADDED", { details: { noteId: "n4", kind: "GENERAL" }, noteExcerpt: "Prefers", projectId: null, projectTitle: null }),
      );
      renderTimeline();
      const expectations: [RegExp, string, string][] = [
        [/logged a call/, "Call note", "Last edited by Omar Khalil"],
        [/logged a meeting/, "Meeting note", "Meeting note"],
        [/logged an email/, "Email note", "Email note"],
        [/wrote a note/, "Note", "About the person · every position"],
      ];
      for (const [card, title, text] of expectations) {
        await userEvent.click(await screen.findByRole("button", { name: card }));
        const dialog = await screen.findByRole("dialog", { name: title });
        expect(await within(dialog).findAllByText(new RegExp(text))).not.toHaveLength(0);
        await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));
      }
    });

    it("goes to the note itself on Open in Notes, and to the Notes tab for a note it can no longer show", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([callNote]);
      history(
        line(2, "NOTE_ADDED", { details: { noteId: "n1", kind: "CALL" }, noteExcerpt: "Open" }),
        line(1, "NOTE_EDITED", { occurredAt: "2026-10-26T09:00:00Z", details: { noteId: "gone" }, noteExcerpt: null }),
      );
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: /logged a call/ }));
      await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: /Open in Notes/ }));
      expect(destinations.onShowNote).toHaveBeenCalledWith("n1");

      await userEvent.click(screen.getByRole("button", { name: /edited a note/ }));
      await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: /Open Notes/ }));
      expect(destinations.onOpenTab).toHaveBeenCalledWith("notes");
    });
  });

  describe("faces", () => {
    it("shows the researcher's photo where their account has one, and their initials where it has none", async () => {
      history(
        line(2, "NOTE_ADDED", { actorAvatarUrl: "https://example.com/dana.png", noteExcerpt: "With photo" }),
        line(1, "NOTE_ADDED", { occurredAt: "2026-10-26T09:00:00Z", actorUserId: "omar", actorName: "Omar Khalil", noteExcerpt: "Without" }),
      );
      renderTimeline();
      const withPhoto = await screen.findByRole("button", { name: /^Dana Aboud wrote a note/ });
      expect(within(withPhoto).getByRole("img", { name: "Dana Aboud" })).toHaveAttribute("src", "https://example.com/dana.png");
      const withoutPhoto = screen.getByRole("button", { name: /^Omar Khalil wrote a note/ });
      expect(within(withoutPhoto).queryByRole("img", { name: "Omar Khalil" })).not.toBeInTheDocument();
      expect(within(withoutPhoto).getByText("OK")).toBeInTheDocument();
    });

    it("falls back to initials when the photo will not load", async () => {
      history(line(1, "NOTE_ADDED", { actorAvatarUrl: "https://example.com/expired.png", noteExcerpt: "Expired photo" }));
      renderTimeline();
      const card = await screen.findByRole("button", { name: /^Dana Aboud wrote a note/ });
      fireEvent.error(within(card).getByRole("img", { name: "Dana Aboud" }));
      expect(within(card).queryByRole("img", { name: "Dana Aboud" })).not.toBeInTheDocument();
      expect(within(card).getByText("DA")).toBeInTheDocument();
    });

    it("puts the face in the dialog too, larger, with who they are", async () => {
      history(line(1, "TAGGED", { actorAvatarUrl: "https://example.com/dana.png", details: { tag: "Referral" } }));
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: /tagged them/ }));
      const dialog = await screen.findByRole("dialog");
      expect(within(dialog).getByRole("img", { name: "Dana Aboud" })).toBeInTheDocument();
      expect(within(dialog).getByText("Your team")).toBeInTheDocument();
    });

    it("names Uncava as the automation and an unnamed actor as someone, never leaving a line anonymous", async () => {
      history(
        line(2, "OUTREACH_STOPPED", { actorUserId: null, actorName: null, details: { sequence: "CFO approach", reason: "BOUNCED" } }),
        line(1, "TAGGED", { occurredAt: "2026-10-26T09:00:00Z", actorName: null, details: { tag: "Referral" } }),
      );
      renderTimeline();
      const stopped = await screen.findByRole("button", { name: /^Automation stopped the sequence/ });
      expect(within(stopped).getByTitle("Automation")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Someone tagged them/ })).toBeInTheDocument();

      await userEvent.click(stopped);
      const dialog = await screen.findByRole("dialog");
      expect(within(dialog).getByText("The address bounced")).toBeInTheDocument();
      expect(within(dialog).getByText("Uncava, automatically")).toBeInTheDocument();
      expect(within(dialog).getByRole("link", { name: /Open conversation/ })).toBeInTheDocument();
    });
  });

  describe("every kind opens under its own title, with its facts and a way to its tab", () => {
    type Case = {
      entry: PersonTimelineEntry;
      card: RegExp;
      title: string;
      shows: (string | RegExp)[];
      way?: { button: RegExp; called: keyof ActivityDestinations; withArgs?: unknown[] } | { link: RegExp };
    };
    const cases: Case[] = [
      {
        entry: line(1, "CONTACTS_EDITED", { details: { emails: "2", phones: "1" } }),
        card: /edited the contacts/, title: "Edited the contacts",
        shows: ["2 emails · 1 phones", /keeps counts only/],
        way: { button: /Open Contact/, called: "onOpenTab", withArgs: ["contact"] },
      },
      {
        entry: line(1, "OWNER_CHANGED", { details: { ownerUserId: "y", owner: "Yara Haddad" } }),
        card: /made Yara Haddad the owner/, title: "Made Yara Haddad the owner", shows: ["Yara Haddad"],
      },
      {
        entry: line(1, "ADDED_TO_POOL", { details: { door: "CSV" } }),
        card: /added them to Group CFO/, title: "Added to Group CFO", shows: ["Imported from a spreadsheet"],
      },
      {
        entry: line(1, "MAPPED", { details: { door: "EXTENSION" } }),
        card: /added them to Group CFO/, title: "Added to Group CFO",
        shows: ["Captured with the plugin", /added rather than duplicated/],
      },
      {
        entry: line(1, "TRIAGE_STATUS_CHANGED", { details: { from: "in_universe", to: "declined", withCompany: "true" } }),
        card: /moved them to/, title: "Moved them to Declined", shows: ["Yes, the company moved too"],
      },
      {
        entry: line(1, "UPDATED_FROM_CALL", {
          details: { call: "Intro call", callAt: "2026-10-22T12:00:00Z", fields: "noticePeriod" },
          changes: [{ field: "noticePeriod", from: null, to: "1 month" }],
        }),
        card: /applied changes from a call/, title: "Changes from a call", shows: ["Intro call", "1 month", "—"],
        way: { button: /Open in Meetings/, called: "onOpenMeetings" },
      },
      {
        entry: line(1, "UPDATED_FROM_CALL", { details: { fields: "noticePeriod,task" } }),
        card: /applied changes from a call/, title: "Changes from a call", shows: ["Notice period, A task"],
        way: { button: /Open in Meetings/, called: "onOpenMeetings" },
      },
      {
        entry: line(1, "AI_ASSESSED", { details: { scope: "BACKGROUND" } }),
        card: /had the AI suggest the background/, title: "Had the AI suggest the background",
        shows: [/marked AI until a researcher changes it/],
        way: { button: /Open Career/, called: "onOpenTab", withArgs: ["career"] },
      },
      {
        entry: line(1, "AI_ASSESSED", { details: { scope: "ASSESSMENT" } }),
        card: /had the AI assess them/, title: "Had the AI assess them", shows: [/drafted a scorecard/],
        way: { button: /Open Assessment/, called: "onOpenTab", withArgs: ["assessment"] },
      },
      {
        entry: line(1, "ASSESSMENT_REVIEWED"),
        card: /reviewed the assessment/, title: "Reviewed the assessment", shows: [],
        way: { button: /Open Assessment/, called: "onOpenTab", withArgs: ["assessment"] },
      },
      {
        entry: line(1, "DO_NOT_CONTACT_SET"),
        card: /marked them do not contact/, title: "Marked them do not contact", shows: [],
        way: { button: /Open Contact/, called: "onOpenTab", withArgs: ["contact"] },
      },
      {
        entry: line(1, "DOCUMENT_REMOVED", { details: { category: "CV", documentId: "d1" } }),
        card: /deleted a CV/, title: "Deleted a CV", shows: [/keeps no copy of the file/],
        way: { button: /Open Documents/, called: "onOpenTab", withArgs: ["documents"] },
      },
      {
        entry: line(1, "OUTREACH_ENROLLED", { details: { sequence: "CFO approach" } }),
        card: /added them to a sequence/, title: "Added them to a sequence", shows: ["CFO approach"],
        way: { link: /Open conversation/ },
      },
      {
        entry: line(1, "OUTREACH_STOPPED", { details: { sequence: "CFO approach" } }),
        card: /stopped the sequence/, title: "Stopped the sequence", shows: ["Stopped by hand"],
      },
      {
        entry: line(1, "EMAIL_REPLIED", { actorUserId: null, actorName: null, details: {} }),
        card: /^Fatima Al Mazrouei replied/, title: "Reply", shows: [/The email itself is in the conversation/, "The executive"],
        way: { link: /Open conversation/ },
      },
      {
        entry: line(1, "TASK_REASSIGNED", { details: { taskId: "t1", title: "Call back", assignee: "Omar Khalil", previousAssignee: "Dana Aboud" } }),
        card: /handed a task over/, title: "Handed a task over", shows: ["Call back", "Omar Khalil"],
        way: { button: /Open task/, called: "onOpenTask", withArgs: ["t1"] },
      },
      {
        entry: line(1, "STATUS_CHANGED", { details: { to: "engaged", automation: "CALL_BOOKED" } }),
        card: /^Automation moved the status/, title: "Status changed to Engaged",
        shows: ["Moved automatically by the pipeline's rule", "Dana Aboud's booked call"],
      },
    ];

    for (const { entry, card, title, shows, way } of cases) {
      it(`${entry.kind} ${JSON.stringify(entry.details)}`, async () => {
        vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [], done: [], doneCount: 0 });
        history(entry);
        renderTimeline();
        await userEvent.click(await screen.findByRole("button", { name: card }));
        const dialog = await screen.findByRole("dialog", { name: title });
        for (const text of shows) {
          expect((await within(dialog).findAllByText(text)).length, String(text)).toBeGreaterThan(0);
        }
        if (!way) {
          expect(within(dialog).queryByRole("button", { name: /^Open/ })).not.toBeInTheDocument();
          return;
        }
        if ("link" in way) {
          expect(within(dialog).getByRole("link", { name: way.link })).toHaveAttribute("href", "/projects/p1/outreach?candidate=c1");
          return;
        }
        await userEvent.click(within(dialog).getByRole("button", { name: way.button }));
        expect(destinations[way.called]).toHaveBeenCalledWith(...(way.withArgs ?? []));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    }

    it("says a read failed for a call and for a task, keeping what the line knows", async () => {
      vi.mocked(meetingApi.getMeetings).mockRejectedValue(failure(500));
      vi.mocked(tasksApi.getCandidateTasks).mockRejectedValue(failure(500));
      history(
        line(2, "MEETING_RESCHEDULED", { details: { startsAt: "2026-10-30T11:00:00Z", previousStartsAt: "2026-10-29T11:00:00Z" } }),
        line(1, "TASK_CREATED", { occurredAt: "2026-10-26T09:00:00Z", details: { taskId: "t1", title: "Prepare questions" } }),
      );
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: /moved a call/ }));
      let dialog = await screen.findByRole("dialog", { name: "Call moved" });
      expect(await within(dialog).findByRole("alert")).toBeInTheDocument();
      expect(within(dialog).getByText("Moved from")).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

      await userEvent.click(screen.getByRole("button", { name: /created a task/ }));
      dialog = await screen.findByRole("dialog", { name: "Created a task" });
      expect(await within(dialog).findByRole("alert")).toBeInTheDocument();
      expect(within(dialog).getByText("Prepare questions")).toBeInTheDocument();
    });

    it("finds no call for a line that kept no time, and no preview for a document with no file", async () => {
      vi.mocked(meetingApi.getMeetings).mockResolvedValue({ upcoming: [meeting({})], past: [] });
      const empty = { ...documentWith("doc-1"), versions: [] };
      history(
        line(2, "MEETING_BOOKED", { details: {} }),
        line(1, "DOCUMENT_ADDED", { occurredAt: "2026-10-26T09:00:00Z", details: { documentId: "doc-1", category: "CV" } }),
      );
      renderTimeline([empty]);
      await userEvent.click(await screen.findByRole("button", { name: /booked a call/ }));
      let dialog = await screen.findByRole("dialog", { name: "Call booked" });
      expect(await within(dialog).findByText(/no longer on the calendar/)).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

      await userEvent.click(screen.getByRole("button", { name: /uploaded a CV/ }));
      dialog = await screen.findByRole("dialog");
      expect(within(dialog).queryByRole("button", { name: "Preview" })).not.toBeInTheDocument();
      expect(within(dialog).getByText(/was deleted since/)).toBeInTheDocument();
    });
  });

  describe("menus and folds undo what they did", () => {
    it("unticks an option, clears from the menu, and closes a fold again", async () => {
      history(
        line(4, "NOTE_ADDED", { occurredAt: "2026-10-28T10:00:00Z", noteExcerpt: "A" }),
        line(3, "TAGGED", { occurredAt: "2026-10-27T09:14:00Z", details: { tag: "x" } }),
        line(2, "TAGGED", { occurredAt: "2026-10-27T09:12:00Z", details: { tag: "y" } }),
        line(1, "TAGGED", { occurredAt: "2026-10-27T09:10:00Z", details: { tag: "z" } }),
      );
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: "Filter by kind" }));
      const menu = screen.getByRole("menu", { name: "Show" });
      const notes = within(menu).getByRole("menuitemcheckbox", { name: /Notes/ });
      await userEvent.click(notes);
      expect(screen.getByText("1 of 4")).toBeInTheDocument();
      await userEvent.click(notes);
      expect(screen.getByText("4 events")).toBeInTheDocument();
      await userEvent.click(notes);
      await userEvent.click(within(menu).getByRole("button", { name: "Show all" }));
      expect(screen.getByText("4 events")).toBeInTheDocument();
      await userEvent.keyboard("{Escape}");

      const fold = screen.getByRole("button", { name: /made 3 changes/ });
      await userEvent.click(fold);
      expect(fold).toHaveAttribute("aria-expanded", "true");
      await userEvent.click(fold);
      expect(fold).toHaveAttribute("aria-expanded", "false");
    });
  });

  it("draws a kind a newer server writes as a quiet row that still opens", async () => {
    history(line(1, "SOMETHING_NEW" as PersonActivityKind));
    renderTimeline();
    await userEvent.click(await screen.findByRole("button", { name: /^Dana Aboud updated the record/ }));
    expect(await screen.findByRole("dialog", { name: "Updated the record" })).toBeInTheDocument();
  });

  describe("a task's card shows where the task stands now", () => {
    const created = (taskId: string) =>
      line(1, "TASK_CREATED", { occurredAt: "2026-10-26T09:00:00Z", details: { taskId, title: "Debrief Fatima", assignee: "Dana Aboud" } });
    const card = async () => screen.findByRole("button", { name: /created a task/ });

    it("ticks the box and strikes the title once the task is done", async () => {
      history(line(2, "TASK_COMPLETED", { details: { taskId: "t1", title: "Debrief Fatima" } }), created("t1"));
      renderTimeline();
      const task = await card();
      expect(within(task).getByRole("img", { name: "Done" })).toBeInTheDocument();
      expect(within(task).getByText("Debrief Fatima")).toHaveClass("line-through");
      expect(within(task).getByText("Done · Assigned to Dana Aboud")).toBeInTheDocument();
    });

    it("leaves the box empty while the task is open, and again once it is reopened", async () => {
      history(
        line(3, "TASK_REOPENED", { details: { taskId: "t1", title: "Debrief Fatima" } }),
        line(2, "TASK_COMPLETED", { details: { taskId: "t1", title: "Debrief Fatima" } }),
        created("t1"),
      );
      renderTimeline();
      const task = await card();
      expect(within(task).getByRole("img", { name: "Open" })).toBeInTheDocument();
      expect(within(task).getByText("Debrief Fatima")).not.toHaveClass("line-through");
      expect(within(task).getByText("Assigned to Dana Aboud")).toBeInTheDocument();
    });

    it("strikes a cancelled task without ticking it", async () => {
      history(line(2, "TASK_CANCELLED", { details: { taskId: "t1", title: "Debrief Fatima" } }), created("t1"));
      renderTimeline();
      const task = await card();
      expect(within(task).getByRole("img", { name: "Cancelled" })).toBeInTheDocument();
      expect(within(task).getByText("Debrief Fatima")).toHaveClass("line-through");
    });

    it("reads a task by its own lines only, never another task's", async () => {
      history(line(2, "TASK_COMPLETED", { details: { taskId: "t2", title: "Something else" } }), created("t1"));
      renderTimeline();
      expect(within(await card()).getByRole("img", { name: "Open" })).toBeInTheDocument();
    });
  });

  describe("the person-wide timeline on the Candidates page", () => {
    const PERSON: ActivityScope = { kind: "person", personId: "person-1" };
    const read = (...entries: PersonTimelineEntry[]) =>
      vi.mocked(poolApi.getPersonHistory).mockResolvedValue({ entries, truncated: false });
    const twoPositions = () =>
      read(
        line(4, "STATUS_CHANGED", {
          projectId: "p2",
          projectTitle: "Head of Credit Risk",
          details: { from: "identified", to: "contacted" },
        }),
        line(3, "TAGGED", { projectId: null, projectTitle: null, details: { tag: "Referral" } }),
        line(2, "MAPPED", { projectId: "p2", projectTitle: "Head of Credit Risk", details: { door: "MANUAL" } }),
        line(1, "ADDED_TO_POOL", { details: { door: "MANUAL" } }),
      );

    it("reads the person's own history, asks for no position's notes, and names each card's position", async () => {
      twoPositions();
      renderTimeline([], PERSON);

      const status = await screen.findByRole("button", { name: /moved the status/ });
      expect(within(status).getByText("Head of Credit Risk")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /added them to Group CFO/ })).toHaveTextContent("Group CFO");
      expect(within(screen.getByRole("button", { name: /tagged them/ })).queryByText(/Group CFO|Head of Credit Risk/)).toBeNull();
      expect(poolApi.getPersonHistory).toHaveBeenCalledWith("person-1", expect.anything());
      expect(personCrmApi.getPositionHistory).not.toHaveBeenCalled();
      expect(personCrmApi.getPersonNotes).not.toHaveBeenCalled();
      expect(screen.getByText("Start of this person's history")).toBeInTheDocument();
    });

    it("filters by position, the person's own changes being one option of their own", async () => {
      twoPositions();
      renderTimeline([], PERSON);
      await userEvent.click(await screen.findByRole("button", { name: "Filter by position" }));
      const menu = screen.getByRole("menu", { name: "Position" });
      expect(within(menu).getByRole("menuitemcheckbox", { name: /Head of Credit Risk/ })).toHaveTextContent("2");
      expect(within(menu).getByRole("menuitemcheckbox", { name: /About the person/ })).toHaveTextContent("1");

      await userEvent.click(within(menu).getByRole("menuitemcheckbox", { name: /Head of Credit Risk/ }));
      expect(screen.getByText("2 of 4")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /tagged them/ })).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Filter by position" })).toHaveTextContent("Head of Credit Risk");

      await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
      expect(screen.getByText("4 events")).toBeInTheDocument();
    });

    it("shows one position as a plain label, and offers no position filter on a position", async () => {
      read(line(2, "STATUS_CHANGED", { details: { to: "engaged" } }), line(1, "ADDED_TO_POOL"));
      renderTimeline([], PERSON);
      expect(await screen.findByTitle("Only Group CFO so far")).toHaveTextContent("Group CFO");

      vi.mocked(personCrmApi.getPositionHistory).mockResolvedValue({ entries: [line(1, "ADDED_TO_POOL")], truncated: false });
      cleanup();
      renderTimeline();
      await screen.findByRole("button", { name: /added them to Group CFO/ });
      expect(screen.queryByRole("button", { name: "Filter by position" })).not.toBeInTheDocument();
      expect(screen.queryByTitle(/Only Group CFO so far/)).not.toBeInTheDocument();
    });

    it("opens a position's milestone on the Positions tab", async () => {
      twoPositions();
      renderTimeline([], PERSON);
      await userEvent.click(await screen.findByRole("button", { name: /moved the status/ }));
      const dialog = await screen.findByRole("dialog", { name: /Status changed to/ });
      expect(within(dialog).getByText("Head of Credit Risk")).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: /Open Positions/ }));
      expect(destinations.onOpenTab).toHaveBeenCalledWith("positions");
    });

    it("says when the person has nothing recorded, and when the read fails", async () => {
      read();
      renderTimeline([], PERSON);
      expect(await screen.findByText("Nothing recorded for this person yet.")).toBeInTheDocument();

      cleanup();
      vi.mocked(poolApi.getPersonHistory).mockRejectedValue(failure(500));
      renderTimeline([], PERSON);
      expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't load the activity.");
    });
  });

  describe("the detail dialog", () => {
    const open = async (name: RegExp) => {
      await userEvent.click(await screen.findByRole("button", { name }));
      return screen.findByRole("dialog");
    };

    it("shows a profile save's fields old and new, and goes to Career", async () => {
      history(
        line(1, "PROFILE_EDITED", {
          changes: [
            { field: "noticePeriod", from: "1 month", to: "3 months" },
            { field: "baseSalary", from: "AED 85,000", to: "AED 92,000" },
            { field: "yearsExperience", from: "12", to: null },
            { field: "career", from: null, to: null },
          ],
        }),
      );
      renderTimeline();
      expect(await screen.findByText("+2 more")).toBeInTheDocument();

      const dialog = await open(/^Dana Aboud edited the profile/);
      expect(dialog).toHaveAccessibleName("Profile edited · 4 fields");
      const rows = within(dialog).getAllByRole("row");
      expect(rows[1]).toHaveTextContent("Notice period1 month3 months");
      expect(rows[3]).toHaveTextContent("Years of experience12Cleared");
      expect(rows[4]).toHaveTextContent("Career historyEdited");
      expect(within(dialog).getByText("Dana Aboud", { selector: "p" })).toBeInTheDocument();

      await userEvent.click(within(dialog).getByRole("button", { name: /Open Career/ }));
      expect(destinations.onOpenTab).toHaveBeenCalledWith("career");
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("says an older profile edit kept no changes", async () => {
      history(line(1, "PROFILE_EDITED", { changes: undefined }));
      renderTimeline();
      const dialog = await open(/edited the profile/);
      expect(within(dialog).getByText(/No profile field this history tracks changed/)).toBeInTheDocument();
    });

    it("shows a deleted note as deleted, and never quotes it", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([]);
      history(line(1, "NOTE_REMOVED", { details: { noteId: "n1" } }));
      renderTimeline();
      const dialog = await open(/deleted a note/);
      expect(within(dialog).getByText(/This note was deleted\. The history names it and never keeps a copy/)).toBeInTheDocument();
      // The Notes tab no longer holds it, so the dialog offers no way there.
      expect(within(dialog).queryByRole("button", { name: /Open/ })).not.toBeInTheDocument();
    });

    it("says a note is gone when the notes no longer hold it, and a failed read can be tried again", async () => {
      vi.mocked(personCrmApi.getPersonNotes).mockRejectedValue(failure(503));
      history(line(1, "NOTE_ADDED", { details: { noteId: "n1" }, noteExcerpt: "Board seat" }));
      renderTimeline();
      const dialog = await open(/wrote a note/);
      const alert = await within(dialog).findByRole("alert");
      expect(alert).toHaveTextContent("Couldn't load the details.");
      vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([]);
      await userEvent.click(within(alert).getByRole("button", { name: "Try again" }));
      expect(await within(dialog).findByText(/deleted since, or is no longer readable/)).toBeInTheDocument();
    });

    it("opens the email a line names, its subject and words", async () => {
      vi.mocked(conversationApi.getConversation).mockResolvedValue(conversationWith(message({ id: "m1" })));
      history(line(1, "EMAIL_SENT", { details: { manual: "true", messageId: "m1" } }));
      renderTimeline();
      const dialog = await open(/sent an email/);
      expect(await within(dialog).findByText("Following up after our call")).toBeInTheDocument();
      expect(within(dialog).getByText(/role overview is attached/)).toBeInTheDocument();
      expect(within(dialog).getByText("overview.pdf")).toBeInTheDocument();
      expect(within(dialog).getByRole("link", { name: /Open conversation/ })).toHaveAttribute(
        "href",
        "/projects/p1/outreach?candidate=c1",
      );
    });

    it("says the email is gone when the conversation no longer holds it, and never asks without an id", async () => {
      vi.mocked(conversationApi.getConversation).mockResolvedValue(conversationWith(message({ id: "other" })));
      history(
        line(2, "EMAIL_SENT", { details: { messageId: "m1" } }),
        line(1, "EMAIL_SENT", { occurredAt: "2026-10-20T09:00:00Z", details: { sequenceId: "s1", sequence: "CFO approach", step: "1" } }),
      );
      renderTimeline();
      let dialog = await open(/^Dana Aboud sent an email, Tue 27 Oct/);
      expect(await within(dialog).findByText(/no longer in the conversation's history/)).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

      vi.mocked(conversationApi.getConversation).mockClear();
      dialog = await open(/emailed them/);
      expect(within(dialog).getByText("CFO approach")).toBeInTheDocument();
      expect(within(dialog).getByText(/The email itself is in the conversation/)).toBeInTheDocument();
      expect(conversationApi.getConversation).not.toHaveBeenCalled();
    });

    it("keeps the line's facts when the conversation cannot be read", async () => {
      vi.mocked(conversationApi.getConversation).mockRejectedValue(failure(500));
      history(line(1, "EMAIL_SENT", { details: { messageId: "m1", sequenceId: "s1", sequence: "CFO approach", step: "2" } }));
      renderTimeline();
      const dialog = await open(/emailed them/);
      expect(await within(dialog).findByRole("alert")).toHaveTextContent("Couldn't load the details.");
      expect(within(dialog).getByText("CFO approach")).toBeInTheDocument();
    });

    it("finds the booked call by its start, and goes to Meetings", async () => {
      vi.mocked(meetingApi.getMeetings).mockResolvedValue({
        upcoming: [meeting({ startsAt: "2026-10-30T11:00:00.000Z", endsAt: "2026-10-30T11:45:00Z" })],
        past: [],
      });
      history(line(1, "MEETING_BOOKED", { details: { startsAt: "2026-10-30T11:00:00Z" } }));
      renderTimeline();
      const dialog = await open(/booked a call/);
      expect(await within(dialog).findByText("45 minutes")).toBeInTheDocument();
      expect(within(dialog).getByText("Microsoft Teams")).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: /Open in Meetings/ }));
      expect(destinations.onOpenMeetings).toHaveBeenCalled();
    });

    it("says a call that has moved off its time is no longer there, and never reads a cancelled one", async () => {
      vi.mocked(meetingApi.getMeetings).mockResolvedValue({ upcoming: [], past: [] });
      history(
        line(2, "MEETING_CANCELLED", { details: { startsAt: "2026-10-30T11:00:00Z" } }),
        line(1, "MEETING_BOOKED", { occurredAt: "2026-10-20T09:00:00Z", details: { startsAt: "2026-10-29T11:00:00Z" } }),
      );
      renderTimeline();
      let dialog = await open(/cancelled a call/);
      expect(within(dialog).getByText(/cancelled and the executive was told/)).toBeInTheDocument();
      expect(meetingApi.getMeetings).not.toHaveBeenCalled();
      await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

      dialog = await open(/booked a call/);
      expect(await within(dialog).findByText(/no longer on the calendar at that time/)).toBeInTheDocument();
    });

    it("previews a document still on file, and says when it is gone", async () => {
      const cv = documentWith("doc-1");
      history(
        line(2, "DOCUMENT_ADDED", { details: { documentId: "doc-1", category: "CV", document: "Fatima CV" } }),
        line(1, "DOCUMENT_ADDED", { occurredAt: "2026-10-20T09:00:00Z", details: { documentId: "gone", category: "REFERENCE" } }),
      );
      renderTimeline([cv]);
      let dialog = await open(/uploaded a CV/);
      await userEvent.click(within(dialog).getByRole("button", { name: "Preview" }));
      expect(destinations.onPreviewDocument).toHaveBeenCalledWith(cv, cv.versions[0]);

      dialog = await open(/uploaded a reference/);
      expect(within(dialog).getByText(/was deleted since/)).toBeInTheDocument();
    });

    it("previews the very version a line names, and says when that version was deleted rather than show another", async () => {
      const cv = {
        ...documentWith("doc-1"),
        versions: [
          { ...documentWith("doc-1").versions[0], id: "v3", versionNo: 3, fileName: "Fatima_CV_v3.pdf" },
          { ...documentWith("doc-1").versions[0], id: "v1", versionNo: 1, fileName: "Fatima_CV_v1.pdf" },
        ],
      };
      history(
        line(3, "DOCUMENT_VERSION_ADDED", { details: { documentId: "doc-1", category: "CV", version: "2" } }),
        line(2, "DOCUMENT_ADDED", { occurredAt: "2026-10-26T09:00:00Z", details: { documentId: "doc-1", category: "CV", version: "1" } }),
        line(1, "DOCUMENT_ADDED", { occurredAt: "2026-10-25T09:00:00Z", details: { documentId: "doc-1", category: "REFERENCE" } }),
      );
      renderTimeline([cv]);

      let dialog = await open(/uploaded version 2 of the CV/);
      expect(within(dialog).queryByRole("button", { name: "Preview" })).not.toBeInTheDocument();
      expect(within(dialog).getByText(/this version of it, was deleted since/)).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

      dialog = await open(/uploaded a CV/);
      expect(within(dialog).getByText("Fatima_CV_v1.pdf")).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: "Preview" }));
      expect(destinations.onPreviewDocument).toHaveBeenLastCalledWith(cv, cv.versions[1]);

      dialog = await open(/uploaded a reference/);
      await userEvent.click(within(dialog).getByRole("button", { name: "Preview" }));
      expect(destinations.onPreviewDocument).toHaveBeenLastCalledWith(cv, cv.versions[0]);
    });

    it("names an addition made from the Candidates page as one to your candidates, not to this position", async () => {
      history(line(1, "ADDED_TO_POOL", { projectId: null, projectTitle: null, details: { door: "MANUAL" } }));
      renderTimeline();
      await userEvent.click(await screen.findByRole("button", { name: /^Dana Aboud added them to your candidates/ }));
      expect(await screen.findByRole("dialog", { name: "Added to your candidates" })).toBeInTheDocument();
    });

    it("shows a task with its state now, and opens it", async () => {
      vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({
        open: [],
        done: [{ id: "t1", status: "done", dueAt: "2026-10-30T10:00:00Z", assignee: { userId: "dana", fullName: "Dana Aboud", avatarUrl: null } } as never],
        doneCount: 1,
      });
      history(line(1, "TASK_CREATED", { details: { taskId: "t1", title: "Prepare call questions", assignee: "Dana Aboud" } }));
      renderTimeline();
      const dialog = await open(/created a task/);
      expect(await within(dialog).findByText("Done")).toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole("button", { name: /Open task/ }));
      expect(destinations.onOpenTask).toHaveBeenCalledWith("t1");
    });

    it("says a task is done from its history when the task list no longer carries it", async () => {
      vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [], done: [], doneCount: 4 });
      history(
        line(2, "TASK_COMPLETED", { details: { taskId: "t1", title: "Prepare call questions" } }),
        line(1, "TASK_CREATED", { occurredAt: "2026-10-26T09:00:00Z", details: { taskId: "t1", title: "Prepare call questions" } }),
      );
      renderTimeline();
      const dialog = await open(/created a task/);
      await vi.waitFor(() => expect(tasksApi.getCandidateTasks).toHaveBeenCalled());
      expect(await within(dialog).findByText("Done")).toBeInTheDocument();
    });

    it("never quotes a contact, only what was found", async () => {
      history(line(1, "CONTACT_FOUND", { details: { channel: "EMAIL", found: "1", via: "CONTACTOUT" } }));
      renderTimeline();
      const dialog = await open(/found an email/);
      expect(within(dialog).getByText("1 found")).toBeInTheDocument();
      expect(within(dialog).getByText(/keeps no address or number/)).toBeInTheDocument();
    });

    it("closes on Escape", async () => {
      history(line(1, "TAGGED", { details: { tag: "Referral" } }));
      renderTimeline();
      await open(/tagged them/);
      await userEvent.keyboard("{Escape}");
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
});

function message(overrides: Partial<ConversationMessage>): ConversationMessage {
  return {
    id: "m1",
    direction: "OUTBOUND",
    channel: "EMAIL",
    kind: "EMAIL",
    status: "SENT",
    at: "2026-10-27T14:02:00Z",
    subject: "Following up after our call",
    text: "Fatima, the role overview is attached.",
    format: "TEXT",
    fromAddress: "dana@meridian.example",
    toAddress: "fatima@example.com",
    senderUserId: "dana",
    senderName: "Dana Aboud",
    enrollmentId: null,
    sequenceName: null,
    stepNumber: null,
    stepCount: null,
    failReason: null,
    attachments: [{ id: "a1", fileName: "overview.pdf", contentType: "application/pdf", sizeBytes: 100 }],
    ...overrides,
  } as ConversationMessage;
}

function conversationWith(...messages: ConversationMessage[]): Conversation {
  return {
    events: messages.map((each) => ({ type: "MESSAGE" as const, at: each.at, message: each })),
    truncated: false,
  } as unknown as Conversation;
}

function meeting(overrides: Partial<meetingApi.Meeting>): meetingApi.Meeting {
  return {
    id: "meet-1",
    title: "Second call",
    startsAt: "2026-10-30T11:00:00Z",
    endsAt: "2026-10-30T11:45:00Z",
    ownerUserId: "dana",
    ownerName: "Dana Aboud",
    joinUrl: null,
    videoProvider: "Microsoft Teams",
    viaLink: false,
    bookedInUncava: true,
    editable: true,
    ...overrides,
  };
}

function documentWith(id: string): PersonDocument {
  return {
    id,
    category: "cv",
    title: "Fatima CV",
    primaryCv: true,
    projectId: null,
    projectTitle: null,
    createdBy: "dana",
    createdByName: "Dana Aboud",
    createdAt: "2026-10-27T09:00:00Z",
    updatedAt: "2026-10-27T09:00:00Z",
    removable: true,
    versions: [
      {
        id: "v1",
        versionNo: 1,
        fileName: "Fatima_CV.pdf",
        contentType: "application/pdf",
        sizeBytes: 1000,
        uploadedBy: "dana",
        uploadedByName: "Dana Aboud",
        uploadedAt: "2026-10-27T09:00:00Z",
        previewable: true,
        removable: true,
      },
    ],
  } as PersonDocument;
}
