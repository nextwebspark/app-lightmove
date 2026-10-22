import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateEmail, PersonRecord } from "../../candidates/api/types";
import * as mailboxApi from "../api/mailboxApi";
import * as meetingApi from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import * as zoomApi from "../api/zoomApi";
import type { Meeting, PersonMeetings } from "../api/meetingApi";
import type { NotetakerMeeting, NotetakerMeetings } from "../api/notetakerApi";
import { MeetingsSection } from "./MeetingsSection";
import { MeetingNoteRecording } from "./NotetakerControls";

vi.mock("../api/meetingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/meetingApi")>()),
  getMeetings: vi.fn(),
  getMeetingSlots: vi.fn(),
  bookMeeting: vi.fn(),
  rescheduleMeeting: vi.fn(),
  cancelMeeting: vi.fn(),
}));
vi.mock("../api/zoomApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/zoomApi")>()),
  getZoom: vi.fn(),
}));
vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));
vi.mock("../../workspace/lib/vocabulary", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../workspace/lib/vocabulary")>();
  return { ...actual, useWorkspaceVocabulary: () => actual.vocabularyFor("COMPANY") };
});
vi.mock("../api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/notetakerApi")>()),
  getNotetakerMeetings: vi.fn(),
  getWorkspaceNotetaker: vi.fn(),
  setMeetingNotetaker: vi.fn(),
  removeNotetaker: vi.fn(),
  retryNotes: vi.fn(),
  getTranscript: vi.fn(),
}));
vi.mock("../../candidates/api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/poolApi")>()),
  getPerson: vi.fn(),
}));

const EMAILS: CandidateEmail[] = [
  { address: "priya@target.example", kind: "work", verified: true, status: null, source: "manual", foundAt: "2026-09-01T00:00:00Z" },
];

function meeting(id: string, title: string, startsAt: string, joinUrl: string | null): Meeting {
  return {
    id,
    title,
    startsAt,
    endsAt: new Date(new Date(startsAt).getTime() + 45 * 60_000).toISOString(),
    ownerUserId: "u1",
    ownerName: "Yara Haddad",
    joinUrl,
    videoProvider: joinUrl ? "Google Meet" : null,
    viaLink: false,
    bookedInUncava: false,
    editable: false,
  };
}

const UPCOMING = meeting("m-next", "Referencing call", "2099-10-02T07:00:00Z", "https://meet.google.com/abc");
const TEAMS = meeting("m-teams", "Reward follow-up", "2099-10-06T12:00:00Z", "https://teams.microsoft.com/l/x");
const LIVE = meeting("m-live", "Second conversation", "2099-09-30T10:00:00Z", "https://meet.google.com/live");
const PAST = meeting("m-past", "First conversation", "2026-09-18T06:00:00Z", "https://meet.google.com/past");

function onCall(meetingId: string, overrides: Partial<NotetakerMeeting> = {}): NotetakerMeeting {
  return {
    meetingId,
    platform: "GOOGLE_MEET",
    joinable: true,
    enabled: true,
    recordingId: null,
    status: null,
    endReason: null,
    recorder: null,
    joinedAt: null,
    recordedUntil: null,
    transcriptHere: false,
    noteId: null,
    notesElsewhere: false,
    ...overrides,
  };
}

function renderSection({
  meetings = { upcoming: [UPCOMING], past: [] },
  notetaker,
}: {
  meetings?: PersonMeetings;
  notetaker: Partial<NotetakerMeetings> & { meetings: NotetakerMeeting[] };
}) {
  vi.mocked(meetingApi.getMeetings).mockResolvedValue(meetings);
  vi.mocked(zoomApi.getZoom).mockResolvedValue({ offered: false, status: null, connectedAt: null });
  vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
    offered: false,
    providers: [],
    bookingLinkOffered: false,
    connection: null,
  } as unknown as mailboxApi.Mailbox);
  vi.mocked(poolApi.getPerson).mockResolvedValue({ doNotContact: null } as PersonRecord);
  vi.mocked(notetakerApi.getNotetakerMeetings).mockResolvedValue({
    offered: true,
    workspaceEnabled: true,
    myMeetingsEnabled: true,
    recordings: [],
    ...notetaker,
  });
  vi.mocked(notetakerApi.getWorkspaceNotetaker).mockResolvedValue({
    offered: true,
    enabled: true,
    platforms: ["GOOGLE_MEET"],
  });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <ToastProvider>
          <MeetingsSection
            projectId="p1"
            candidateId="c1"
            personId="person-1"
            fullName="Priya Raman"
            candidateStatus="contacted"
            emails={EMAILS}
          />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("Uncava Notetaker in Meetings", () => {
  beforeEach(() => vi.resetAllMocks());

  it("switches the notetaker off for one call to come", async () => {
    vi.mocked(notetakerApi.setMeetingNotetaker).mockResolvedValue(undefined);
    renderSection({ notetaker: { meetings: [onCall("m-next")] } });

    const toggle = await screen.findByRole("switch", { name: "Uncava Notetaker joins this call" });
    expect(toggle).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("Notetaker joins")).toBeInTheDocument();
    await userEvent.click(toggle);

    await waitFor(() => expect(notetakerApi.setMeetingNotetaker).toHaveBeenCalledWith("p1", "c1", "m-next", false));
    expect(await screen.findByText("The notetaker won't join this call")).toBeInTheDocument();
  });

  it("says why a call is never joined, from the platforms the bot joins", async () => {
    renderSection({
      meetings: { upcoming: [TEAMS], past: [] },
      notetaker: { meetings: [onCall("m-teams", { platform: "MICROSOFT_TEAMS", joinable: false })] },
    });

    expect(await screen.findByText("The notetaker joins Google Meet calls.")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("greys every switch, and says why, while the consultant has the notetaker off", async () => {
    renderSection({ notetaker: { myMeetingsEnabled: false, meetings: [onCall("m-next")] } });

    const toggle = await screen.findByRole("switch", { name: "Uncava Notetaker joins this call" });
    expect(toggle).toBeDisabled();
    expect(toggle).toHaveAttribute("aria-checked", "false");
    expect(screen.getByText("You've turned the notetaker off for your calls.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Profile" })).toHaveAttribute("href", "/settings/profile");
  });

  it("removes a recording notetaker only once asked, keeping the call recorded otherwise", async () => {
    vi.mocked(notetakerApi.removeNotetaker).mockResolvedValue(undefined);
    renderSection({
      meetings: { upcoming: [LIVE], past: [] },
      notetaker: { meetings: [onCall("m-live", { status: "RECORDING", joinedAt: "2099-09-30T10:02:00Z" })] },
    });

    expect(await screen.findByText("Recording")).toHaveAttribute(
      "title",
      expect.stringContaining("It said in the chat that it is recording."),
    );
    await userEvent.click(screen.getByRole("button", { name: "Remove notetaker" }));
    await userEvent.click(screen.getByRole("button", { name: "Keep recording" }));
    expect(notetakerApi.removeNotetaker).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Remove notetaker" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove notetaker" }));
    await waitFor(() => expect(notetakerApi.removeNotetaker).toHaveBeenCalledWith("p1", "c1", "m-live"));
  });

  it("offers nothing more while the bot is leaving", async () => {
    renderSection({
      meetings: { upcoming: [LIVE], past: [] },
      notetaker: { meetings: [onCall("m-live", { status: "REMOVE_REQUESTED" })] },
    });

    expect(await screen.findByText("Leaving…")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove notetaker" })).not.toBeInTheDocument();
  });

  it("says why a bot couldn't record, from its end reason", async () => {
    renderSection({
      meetings: { upcoming: [], past: [PAST] },
      notetaker: { meetings: [onCall("m-past", { status: "FAILED", endReason: "CONSENT_NOT_SHOWN" })] },
    });

    expect(await screen.findByText("Couldn't record")).toHaveAttribute(
      "title",
      "It couldn't say in the chat that it was recording, so it left without recording.",
    );
  });

  it("tries a failed note again", async () => {
    vi.mocked(notetakerApi.retryNotes).mockResolvedValue(undefined);
    renderSection({
      meetings: { upcoming: [], past: [PAST] },
      notetaker: { meetings: [onCall("m-past", { status: "FAILED", endReason: "PROCESSING_FAILED" })] },
    });

    expect(await screen.findByText("Notes failed")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    await waitFor(() => expect(notetakerApi.retryNotes).toHaveBeenCalledWith("p1", "c1", "m-past"));
    expect(await screen.findByText("Trying again. The note follows in a few minutes.")).toBeInTheDocument();
  });

  it("opens a partial call's transcript, its unnamed voice drawn apart", async () => {
    vi.mocked(notetakerApi.getTranscript).mockResolvedValue({
      recordingId: "r1",
      status: "DONE",
      complete: true,
      title: "First conversation",
      startsAt: "2026-09-18T06:00:00Z",
      endsAt: "2026-09-18T06:45:00Z",
      joinedAt: "2026-09-18T06:04:00Z",
      consultantName: "Yara Haddad",
      recordedUntil: "2026-09-18T06:31:00Z",
      lines: [
        { at: "2026-09-18T06:04:04Z", speaker: "Yara Haddad", text: "Thanks for making the time.", named: true },
        { at: "2026-09-18T06:04:19Z", speaker: "Priya Raman", text: "Of course, the portfolio interests me.", named: true },
        { at: "2026-09-18T06:08:47Z", speaker: "Speaker 3", text: "The vesting schedule is in the pack.", named: false },
      ],
    });
    renderSection({
      meetings: { upcoming: [], past: [PAST] },
      notetaker: {
        meetings: [
          onCall("m-past", {
            status: "DONE",
            recordingId: "r1",
            recordedUntil: "2026-09-18T06:31:00Z",
            transcriptHere: true,
            noteId: "n1",
          }),
        ],
      },
    });

    expect(await screen.findByText("Notes ready · partial")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "View transcript" }));

    const sheet = await screen.findByRole("dialog", { name: "Transcript" });
    expect(await within(sheet).findByText("The vesting schedule is in the pack.")).toBeInTheDocument();
    expect(within(sheet).getByText("Speaker 3")).toHaveClass("italic");
    expect(notetakerApi.getTranscript).toHaveBeenCalledWith("p1", "c1", "r1", expect.anything());
    // Counted from when the bot was let in, as the note's evidence is, not from the calendar's start.
    expect(within(sheet).getByText("00:04:47")).toBeInTheDocument();
    expect(within(sheet).getByText("3 lines · 27 minutes")).toBeInTheDocument();
    expect(within(sheet).getByText("Yara Haddad")).toHaveClass("text-u-accent");
    expect(within(sheet).getByText(/^Partial: the notetaker stopped at/)).toBeInTheDocument();
    expect(within(sheet).getByText(/Hiring managers never see it\./)).toBeInTheDocument();

    await userEvent.type(within(sheet).getByRole("searchbox", { name: "Search the transcript" }), "portfolio");
    expect(within(sheet).getByText("1 of 3 lines")).toBeInTheDocument();
    expect(within(sheet).queryByText("Thanks for making the time.")).not.toBeInTheDocument();
  });

  it("names no position a call's note went to, and offers no transcript from here", async () => {
    renderSection({
      meetings: { upcoming: [], past: [PAST] },
      notetaker: { meetings: [onCall("m-past", { status: "DONE", notesElsewhere: true })] },
    });

    expect(await screen.findByText("On another position")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "View transcript" })).not.toBeInTheDocument();
  });

  it("draws nothing of the notetaker where the deployment sends none", async () => {
    renderSection({ notetaker: { offered: false, meetings: [onCall("m-next")] } });

    expect(await screen.findByText("Referencing call")).toBeInTheDocument();
    await waitFor(() => expect(notetakerApi.getNotetakerMeetings).toHaveBeenCalled());
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("marks a Meeting note the notetaker filed, its transcript reachable with its meeting row gone", async () => {
    vi.mocked(notetakerApi.getNotetakerMeetings).mockResolvedValue({
      offered: true,
      workspaceEnabled: true,
      myMeetingsEnabled: true,
      meetings: [],
      recordings: [{ recordingId: "r1", noteId: "n1", recordedUntil: "2026-09-18T06:31:00Z" }],
    });
    vi.mocked(notetakerApi.getTranscript).mockReturnValue(new Promise(() => {}));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { unmount } = render(
      <QueryClientProvider client={client}>
        <MeetingNoteRecording projectId="p1" candidateId="c1" noteId="n1" />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Recorded by Uncava Notetaker")).toBeInTheDocument();
    expect(screen.getByText(/^Partial · stopped at/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "View transcript" }));
    expect(notetakerApi.getTranscript).toHaveBeenCalledWith("p1", "c1", "r1", expect.anything());
    unmount();

    render(
      <QueryClientProvider client={client}>
        <MeetingNoteRecording projectId="p1" candidateId="c1" noteId="typed-note" />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(notetakerApi.getNotetakerMeetings).toHaveBeenCalled());
    expect(screen.queryByText("Recorded by Uncava Notetaker")).not.toBeInTheDocument();
  });
});
