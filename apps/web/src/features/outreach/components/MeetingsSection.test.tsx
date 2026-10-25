import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateEmail, PersonRecord } from "../../candidates/api/types";
import * as mailboxApi from "../api/mailboxApi";
import * as meetingApi from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import * as zoomApi from "../api/zoomApi";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PersonMeetings } from "../api/meetingApi";
import { openCopilotWindow } from "../lib/copilotWindow";
import { MeetingsSection } from "./MeetingsSection";

vi.mock("../api/meetingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/meetingApi")>()),
  getMeetings: vi.fn(),
  getMeetingCalendar: vi.fn(),
  bookMeeting: vi.fn(),
  rescheduleMeeting: vi.fn(),
  cancelMeeting: vi.fn(),
}));
vi.mock("../api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/notetakerApi")>()),
  getNotetaker: vi.fn(),
  scheduleNotetaker: vi.fn(),
  cancelNotetaker: vi.fn(),
  retryNotetaker: vi.fn(),
  getTranscript: vi.fn(),
  getCallSuggestions: vi.fn(),
  suggestChanges: vi.fn(),
  switchLiveCopilot: vi.fn(),
  getCopilot: vi.fn(),
  getInterviewPlan: vi.fn(),
  draftInterviewPlan: vi.fn(),
}));
vi.mock("../lib/copilotWindow", () => ({ openCopilotWindow: vi.fn() }));

const onShowCopilot = vi.fn();
vi.mock("../api/zoomApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/zoomApi")>()),
  getZoom: vi.fn(),
}));
vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));
vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
  statusAutomations: vi.fn(),
}));
vi.mock("../../candidates/api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/poolApi")>()),
  getPerson: vi.fn(),
}));

const EMAILS: CandidateEmail[] = [
  {
    address: "priya@target.example",
    kind: "work",
    verified: true,
    status: null,
    source: "manual",
    foundAt: "2026-09-01T00:00:00Z",
  },
];

const MEETINGS: PersonMeetings = {
  upcoming: [
    {
      id: "m1",
      title: "Confidential: first conversation",
      startsAt: "2026-10-07T06:00:00Z",
      endsAt: "2026-10-07T06:30:00Z",
      ownerUserId: "u1",
      ownerName: "Yara Haddad",
      joinUrl: "https://meet.google.com/abc",
      videoProvider: "Google Meet",
      viaLink: false,
      bookedInUncava: true,
      editable: true,
    },
  ],
  past: [
    {
      id: "m2",
      title: "Coffee",
      startsAt: "2026-09-22T06:00:00Z",
      endsAt: "2026-09-22T06:30:00Z",
      ownerUserId: "u2",
      ownerName: "Sara Al-Mansour",
      joinUrl: null,
      videoProvider: null,
      viaLink: false,
      bookedInUncava: false,
      editable: false,
    },
  ],
};

function renderSection({
  meetings = MEETINGS,
  doNotContact = false,
  offered = true,
  zoomStatus = null,
  notetakerOffered = false,
  recordings = [],
  recordableMeetingIds = ["m1"],
}: {
  meetings?: PersonMeetings;
  doNotContact?: boolean;
  offered?: boolean;
  zoomStatus?: zoomApi.ZoomAccount["status"];
  notetakerOffered?: boolean;
  recordings?: NotetakerRecording[];
  recordableMeetingIds?: string[];
} = {}) {
  vi.mocked(meetingApi.getMeetings).mockResolvedValue(meetings);
  vi.mocked(notetakerApi.getNotetaker).mockResolvedValue({
    offered: notetakerOffered,
    recordings,
    recordableMeetingIds: notetakerOffered && !doNotContact ? recordableMeetingIds : [],
  });
  vi.mocked(zoomApi.getZoom).mockResolvedValue({ offered: true, status: zoomStatus, connectedAt: null });
  vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
    offered,
    providers: ["google"],
    bookingLinkOffered: false,
    connection: {
      address: "yara@firm.example",
      provider: "google",
      status: "ACTIVE",
      dailyCap: 50,
      timeZone: "Asia/Dubai",
      connectedAt: "2026-09-01T00:00:00Z",
      movesOffNylas: false,
      runsStoppedByMove: 0,
      liveSequences: 0,
      livePeople: 0,
      readsReplies: false,
      reconnectToReadReplies: false,
      sentToday: 0,
      scheduledEmails: 0,
    },
  });
  vi.mocked(poolApi.getPerson).mockResolvedValue({
    doNotContact: doNotContact
      ? { reason: "Asked not to be approached.", setByUserId: "u1", setByName: "Yara", setAt: "2026-09-01T00:00:00Z" }
      : null,
  } as PersonRecord);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <MeetingsSection
          projectId="p1"
          candidateId="c1"
          personId="person-1"
          fullName="Priya Raman"
          candidateStatus="contacted"
          emails={EMAILS}
          onShowCopilot={onShowCopilot}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("MeetingsSection", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // Every upcoming row reads its call's plan; left unanswered, that read settles after the test has ended.
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue({
      meetingId: "m1",
      title: null,
      startsAt: "2026-10-07T06:00:00Z",
      drafted: false,
      savedAt: null,
      savedByName: null,
      competencies: [],
      missing: [],
      questions: [],
    });
  });

  it("lists upcoming meetings with a join link and past ones without", async () => {
    renderSection();

    expect(await screen.findByText("Confidential: first conversation")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google Meet" })).toHaveAttribute("href", "https://meet.google.com/abc");
    expect(screen.getByText("Coffee")).toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(await screen.findByRole("button", { name: "Book a call" })).toBeInTheDocument();
  });

  it("says when nobody on the team has met them yet", async () => {
    renderSection({ meetings: { upcoming: [], past: [] } });

    expect(await screen.findByText("No meetings with Priya on your team's calendars yet.")).toBeInTheDocument();
  });

  it("offers no Book a call where the deployment has no mail service, whatever row is on file", async () => {
    renderSection({ offered: false });

    await screen.findByText("Coffee");
    await waitFor(() => expect(mailboxApi.getMailbox).toHaveBeenCalled());
    await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Book a call" })).not.toBeInTheDocument();
  });

  it("offers no Book a call for someone marked do not contact", async () => {
    renderSection({ doNotContact: true });

    await screen.findByText("Coffee");
    await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
    await waitFor(() => expect(mailboxApi.getMailbox).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Book a call" })).not.toBeInTheDocument();
  });

  it("books a time picked on the consultant's calendar", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(aSeededCatalog());
    vi.mocked(pipelineApi.statusAutomations).mockResolvedValue([
      { trigger: "callBooked", enabled: true, statusId: "status-engaged", broken: false },
    ]);
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await waitFor(() => expect(pipelineApi.statusAutomations).toHaveBeenCalled());
    expect(await screen.findByRole("group", { name: "Your week" })).toBeInTheDocument();
    const send = screen.getByRole("button", { name: "Send invite" });
    expect(send).toBeDisabled();

    pickTime("10:30");
    expect(screen.getByText("Mon 5 Oct")).toBeInTheDocument();
    expect(screen.getByText("10:30 – 11:00 · 30 min")).toBeInTheDocument();
    await userEvent.click(send);

    await waitFor(() =>
      expect(meetingApi.bookMeeting).toHaveBeenCalledWith("p1", "c1", {
        startsAt: "2099-10-05T06:30:00.000Z",
        minutes: 30,
        video: "GOOGLE_MEET",
        inviteAddress: "priya@target.example",
        title: "Confidential: first conversation",
      }),
    );
    expect(
      await screen.findByText(/Invite sent to Priya for Mon 5 Oct, 10:30\. They moved to Engaged\./),
    ).toBeInTheDocument();
  });

  it("books any length, on any minute, and says when a time is outside working hours", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("19:40");
    fireEvent.change(screen.getByLabelText("Call ends"), { target: { value: "20:30" } });

    expect(screen.getByText("19:40 – 20:30 · 50 min")).toBeInTheDocument();
    expect(screen.getByText("Outside your working hours")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Other" })).toHaveAttribute("aria-checked", "true");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() =>
      expect(meetingApi.bookMeeting).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ startsAt: "2099-10-05T15:40:00.000Z", minutes: 50 }),
      ),
    );
  });

  it("refuses a time the calendar holds before anything is sent", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(
      aWeek({ busy: [{ start: "2099-10-05T07:00:00Z", end: "2099-10-05T08:00:00Z" }] }),
    );
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("10:45");

    expect(screen.getByRole("alert")).toHaveTextContent("Your calendar is busy then.");
    expect(screen.getByRole("button", { name: "Send invite" })).toBeDisabled();
  });

  it("books the day the time was picked on, and drops a pick the next week does not show", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockImplementation(async (_project, _candidate, from) =>
      aWeek({ from: from ?? "2099-10-05" }),
    );
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    fireEvent.change(screen.getByLabelText("Call date"), { target: { value: "2099-10-10" } });
    pickTime("10:00");
    expect(screen.getByText("Sat 10 Oct")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Later days" }));
    await waitFor(() =>
      expect(meetingApi.getMeetingCalendar).toHaveBeenLastCalledWith("p1", "c1", "2099-10-12", null, expect.anything()),
    );
    expect(await screen.findByText("Drag on your calendar, or type a time")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send invite" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Earlier days" }));
    fireEvent.change(await screen.findByLabelText("Call date"), { target: { value: "2099-10-10" } });
    pickTime("10:00");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));
    await waitFor(() =>
      expect(meetingApi.bookMeeting).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ startsAt: "2099-10-10T06:00:00.000Z" }),
      ),
    );
  });

  it("moves a call booked in Uncava, its own time read as free", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.rescheduleMeeting).mockResolvedValue(undefined);
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Reschedule" }));
    expect(await screen.findByText("Reschedule call with Priya Raman")).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Video link" })).not.toBeInTheDocument();
    await screen.findByRole("group", { name: "Your week" });
    pickTime("11:00");
    await userEvent.click(screen.getByRole("button", { name: "Move call" }));

    await waitFor(() =>
      expect(meetingApi.rescheduleMeeting).toHaveBeenCalledWith("p1", "c1", "m1", {
        startsAt: "2099-10-05T07:00:00.000Z",
        minutes: 30,
      }),
    );
    expect(meetingApi.getMeetingCalendar).toHaveBeenCalledWith("p1", "c1", null, "m1", expect.anything());
    expect(await screen.findByText(/Call with Priya moved to Mon 5 Oct, 11:00\./)).toBeInTheDocument();
  });

  it("cancels a call booked in Uncava once asked, and offers nothing on a call found on a calendar", async () => {
    vi.mocked(meetingApi.cancelMeeting).mockResolvedValue(undefined);
    renderSection({
      meetings: {
        upcoming: [MEETINGS.upcoming[0], { ...MEETINGS.upcoming[0], id: "m3", title: "Lunch", editable: false }],
        past: [],
      },
    });

    expect(await screen.findAllByRole("button", { name: "Cancel call" })).toHaveLength(1);
    await userEvent.click(screen.getByRole("button", { name: "Cancel call" }));
    expect(await screen.findByText("Cancel the call with Priya Raman?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Keep call" })).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole("button", { name: "Cancel call" }).at(-1) as HTMLElement);

    await waitFor(() => expect(meetingApi.cancelMeeting).toHaveBeenCalledWith("p1", "c1", "m1"));
    expect(await screen.findByText("Call with Priya cancelled. They've been told.")).toBeInTheDocument();
  });

  it("offers no Reschedule for someone marked do not contact, though the call can still be cancelled", async () => {
    renderSection({ doNotContact: true });

    expect(await screen.findByRole("button", { name: "Cancel call" })).toBeInTheDocument();
    // Absence proves nothing until the person record, which decides it, has landed.
    await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
    await vi.mocked(poolApi.getPerson).mock.results[0].value;
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByRole("button", { name: "Reschedule" })).not.toBeInTheDocument();
  });

  it("offers only the video link the connected calendar can make", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    const video = screen.getByRole("combobox", { name: "Video link" });
    expect(Array.from(video.querySelectorAll("option"), (option) => option.textContent)).toEqual([
      "Google Meet",
      "No video link",
    ]);
  });

  it("offers Zoom in Book a call only where the consultant's own Zoom account is connected", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek({ zoomOffered: true }));
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    renderSection({ zoomStatus: "ACTIVE" });

    expect(await screen.findByText("Zoom connected")).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("10:00");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Video link" }), "ZOOM");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() =>
      expect(meetingApi.bookMeeting).toHaveBeenCalledWith("p1", "c1", expect.objectContaining({ video: "ZOOM" })),
    );
  });

  it("asks to reconnect a Zoom account Zoom refused", async () => {
    renderSection({ zoomStatus: "ERROR" });

    expect(await screen.findByRole("button", { name: "Reconnect Zoom" })).toBeInTheDocument();
  });

  it("pages the week ahead, never before today", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockImplementation(async (_project, _candidate, from) =>
      aWeek({ from: from ?? "2099-10-05" }),
    );
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    expect(await screen.findByRole("button", { name: "Earlier days" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Later days" }));

    await waitFor(() =>
      expect(meetingApi.getMeetingCalendar).toHaveBeenLastCalledWith("p1", "c1", "2099-10-12", null, expect.anything()),
    );
    expect(await screen.findByRole("button", { name: "Earlier days" })).toBeEnabled();
  });

  it("offers Record with Notetaker on a call with a video link, and sends the bot when pressed", async () => {
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(aRecording({ status: "SCHEDULED" }));
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("switch", { name: "Record with Notetaker" }));

    expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m1", false);
    expect(await screen.findByText(/The Notetaker will join the call with Priya/)).toBeInTheDocument();
  });

  it("offers no Notetaker where none is set up, or for someone marked do not contact", async () => {
    renderSection({ notetakerOffered: false });
    await screen.findByText("Coffee");
    await waitFor(() => expect(notetakerApi.getNotetaker).toHaveBeenCalled());
    expect(screen.queryByRole("switch", { name: "Record with Notetaker" })).not.toBeInTheDocument();
  });

  it("hides Record with Notetaker for someone marked do not contact", async () => {
    renderSection({ notetakerOffered: true, doNotContact: true });
    await screen.findByText("Coffee");
    await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
    await waitFor(() => expect(notetakerApi.getNotetaker).toHaveBeenCalled());
    expect(screen.queryByRole("switch", { name: "Record with Notetaker" })).not.toBeInTheDocument();
  });

  it("offers no Notetaker on a call that is only on a teammate's calendar", async () => {
    renderSection({ notetakerOffered: true, recordableMeetingIds: [] });
    await screen.findByText("Coffee");
    await waitFor(() => expect(notetakerApi.getNotetaker).toHaveBeenCalled());
    expect(screen.queryByRole("switch", { name: "Record with Notetaker" })).not.toBeInTheDocument();
  });

  it("shows a scheduled bot on its call and removes it after asking", async () => {
    vi.mocked(notetakerApi.cancelNotetaker).mockResolvedValue(undefined);
    renderSection({
      meetings: {
        ...MEETINGS,
        upcoming: [{ ...MEETINGS.upcoming[0], startsAt: "2099-10-07T06:00:00Z", endsAt: "2099-10-07T06:30:00Z" }],
      },
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED" })],
    });

    expect(await screen.findByText("Joins 1 min early")).toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: "Record with Notetaker" });
    expect(toggle).toHaveAttribute("aria-checked", "true");
    await userEvent.click(toggle);
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove notetaker" }));

    expect(notetakerApi.cancelNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
  });

  it("offers the Notetaker on a call that has run past its booked end, and says it joins at once", async () => {
    const overrun = { ...MEETINGS.upcoming[0], id: "m4", title: "Ran over" };
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(
      aRecording({ id: "r4", meetingId: "m4", status: "SCHEDULED" }),
    );
    renderSection({
      meetings: { upcoming: [], past: [overrun] },
      notetakerOffered: true,
      recordableMeetingIds: ["m4"],
    });

    expect(await screen.findByText("Past its end · still on?")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("switch", { name: "Record with Notetaker" }));

    expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m4", false);
  });

  it("says a bot sent to a call already under way joins now", async () => {
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "SCHEDULED" })] });

    expect(await screen.findByText("Joining now")).toBeInTheDocument();
  });

  it("offers a call under way to be recorded from here on", async () => {
    renderSection({
      meetings: { ...MEETINGS, upcoming: [{ ...MEETINGS.upcoming[0], endsAt: "2099-10-07T06:30:00Z" }] },
      notetakerOffered: true,
    });

    expect(await screen.findByText("Call is on · not recorded")).toBeInTheDocument();
  });

  it("opens a past call's transcript once its notes are filed, matched by its start", async () => {
    vi.mocked(notetakerApi.getTranscript).mockResolvedValue({
      recordingId: "r2",
      title: "Coffee",
      startsAt: "2026-09-22T06:00:00Z",
      lines: [
        { at: "2026-09-22T06:00:05Z", speaker: "Yara Haddad", text: "Thanks for making the time." },
        { at: "2026-09-22T06:01:10Z", speaker: "Priya Raman", text: "Happy to talk." },
      ],
    });
    renderSection({
      notetakerOffered: true,
      recordings: [
        aRecording({
          id: "r2",
          meetingId: "an-older-row",
          startsAt: "2026-09-22T06:00:00Z",
          title: "Coffee",
          status: "DONE",
          hasTranscript: true,
        }),
      ],
    });

    expect(await screen.findByText("Notes filed")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Transcript" }));

    expect(await screen.findByText("Happy to talk.")).toBeInTheDocument();
    expect(screen.getByText("01:05")).toBeInTheDocument();
  });

  it("offers Suggest changes on a call whose notes are filed, and says what is left to review", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue({
      recordingId: "r2",
      title: "Coffee",
      startsAt: "2026-09-22T06:00:00Z",
      asked: true,
      mayAskAgain: true,
      suggestions: [],
    });
    renderSection({
      notetakerOffered: true,
      recordings: [
        aRecording({
          id: "r2",
          meetingId: "m2",
          startsAt: "2026-09-22T06:00:00Z",
          status: "DONE",
          hasTranscript: true,
        }),
        aRecording({
          id: "r3",
          meetingId: "m1",
          status: "DONE",
          hasTranscript: true,
          suggestionsAsked: true,
          pendingSuggestions: 2,
        }),
      ],
    });

    expect(await screen.findByRole("button", { name: "Review 2 changes" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Suggest changes" }));

    expect(await screen.findByRole("dialog", { name: "Changes from the call" })).toBeInTheDocument();
    expect(await screen.findByText("Nothing to change")).toBeInTheDocument();
    expect(notetakerApi.getCallSuggestions).toHaveBeenCalledWith("p1", "c1", "r2", expect.anything());
  });

  it("says what a review made of a call's changes, and offers no way back into it", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [
        aRecording({
          id: "r2",
          meetingId: "m2",
          startsAt: "2026-09-22T06:00:00Z",
          status: "DONE",
          hasTranscript: true,
          suggestionsAsked: true,
          appliedSuggestions: 3,
        }),
        aRecording({ id: "r3", meetingId: "m1", status: "DONE", hasTranscript: true, suggestionsAsked: true }),
      ],
    });

    expect(await screen.findByText("3 changes applied")).toBeInTheDocument();
    expect(screen.getByText("No changes made")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /changes applied|No changes made|Suggest changes/ })).not.toBeInTheDocument();
  });

  it("offers no Suggest changes once the transcript is no longer kept", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ id: "r2", meetingId: "m2", startsAt: "2026-09-22T06:00:00Z", status: "DONE" })],
    });

    expect(await screen.findByText("Notes filed")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Suggest changes" })).not.toBeInTheDocument();
  });

  it("says why a bot failed, and tries the notes again only where the call was heard", async () => {
    vi.mocked(notetakerApi.retryNotetaker).mockResolvedValue(aRecording({ status: "PROCESSING" }));
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "FAILED", failureReason: "SUMMARY_FAILED", retryable: true })],
    });

    expect(await screen.findByText("Failed: the notes couldn't be written")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(notetakerApi.retryNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
    expect(screen.getByRole("switch", { name: "Record with Notetaker" })).toHaveAttribute("aria-checked", "false");
  });

  it("says once the call is over why no notes were filed, with no switch left to press", async () => {
    renderSection({
      notetakerOffered: true,
      recordableMeetingIds: [],
      recordings: [aRecording({ status: "FAILED", failureReason: "timeout_exceeded_waiting_room" })],
    });

    expect(await screen.findByText("Notetaker failed")).toBeInTheDocument();
    expect(screen.getByText("nobody let it in")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "Record with Notetaker" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
  });

  it("says the notes are being written after the call, with the copilot still a click away", async () => {
    renderSection({
      notetakerOffered: true,
      recordableMeetingIds: [],
      recordings: [aRecording({ status: "PROCESSING", liveCopilot: true })],
    });

    expect(await screen.findByText("Notetaker is writing notes…")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open copilot" })).toBeInTheDocument();
  });

  it("sends the Notetaker to a call it books, switched on by default", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(aRecording({ meetingId: "m9", status: "SCHEDULED" }));
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("10:00");
    const toggle = within(screen.getByRole("dialog")).getByRole("switch", { name: "Record with Notetaker" });
    expect(toggle).toHaveAttribute("aria-checked", "true");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() => expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m9", false));
    expect(await screen.findByText(/The Notetaker will join and file its notes\./)).toBeInTheDocument();
  });

  it("books without the Notetaker once it is switched off", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("10:00");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("switch", { name: "Record with Notetaker" }));
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() => expect(meetingApi.bookMeeting).toHaveBeenCalled());
    expect(await screen.findByText(/Invite sent to Priya/)).toBeInTheDocument();
    expect(notetakerApi.scheduleNotetaker).not.toHaveBeenCalled();
  });

  it("books with the live copilot, which switches the Notetaker on with it", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(aRecording({ meetingId: "m9", liveCopilot: true }));
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    pickTime("10:00");
    const dialog = within(screen.getByRole("dialog"));
    await userEvent.click(dialog.getByRole("switch", { name: "Record with Notetaker" }));
    await userEvent.click(dialog.getByRole("switch", { name: "Live copilot" }));
    expect(dialog.getByRole("switch", { name: "Record with Notetaker" })).toHaveAttribute("aria-checked", "true");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() => expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m9", true));
    expect(await screen.findByText(/the live copilot will follow the call for you/)).toBeInTheDocument();
  });

  it("explains the Notetaker and the live copilot on hover in Book a call, as the call's own row does", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    const dialog = within(screen.getByRole("dialog"));
    const notetaker = dialog.getByRole("switch", { name: "Record with Notetaker" });

    await userEvent.hover(notetaker);
    expect(screen.getByRole("tooltip")).toHaveTextContent(/files the notes on Priya's record/);
    await userEvent.unhover(notetaker);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    await userEvent.hover(dialog.getByRole("switch", { name: "Live copilot" }));
    expect(screen.getByRole("tooltip")).toHaveTextContent(/Switching it on sends the Notetaker too/);
  });

  it("offers no Notetaker in Book a call where none is set up", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    renderSection({ notetakerOffered: false });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    expect(within(screen.getByRole("dialog")).queryByRole("switch")).not.toBeInTheDocument();
  });

  it("sends the Notetaker ready for the live copilot when Live copilot is switched on first", async () => {
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(
      aRecording({ liveCopilot: true, liveStatus: "LISTENING" }),
    );
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("switch", { name: "Live copilot" }));

    expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m1", true);
    expect(await screen.findByText(/the live copilot will follow it for you/)).toBeInTheDocument();
  });

  it("puts both switches on one line and explains each on hover", async () => {
    renderSection({
      meetings: {
        ...MEETINGS,
        upcoming: [{ ...MEETINGS.upcoming[0], startsAt: "2099-10-07T06:00:00Z", endsAt: "2099-10-07T06:30:00Z" }],
      },
      notetakerOffered: true,
    });

    const notetaker = await screen.findByRole("switch", { name: "Record with Notetaker" });
    expect(screen.getByText("Not recorded")).toBeInTheDocument();
    await userEvent.hover(notetaker);
    expect(screen.getByRole("tooltip")).toHaveTextContent(/files the notes on Priya's record/);
    await userEvent.unhover(notetaker);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    await userEvent.hover(screen.getByRole("switch", { name: "Live copilot" }));
    expect(screen.getByRole("tooltip")).toHaveTextContent(/Switching it on sends the Notetaker too/);
  });

  it("switches the live copilot on a Notetaker that has not joined yet, and offers no switch once it has", async () => {
    vi.mocked(notetakerApi.switchLiveCopilot).mockResolvedValue(aRecording({ liveCopilot: true }));
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "SCHEDULED" })] });

    await userEvent.click(await screen.findByRole("switch", { name: "Live copilot" }));
    expect(notetakerApi.switchLiveCopilot).toHaveBeenCalledWith("p1", "c1", "r1", true);
  });

  it("shows a teammate's Notetaker copilot but leaves it to the teammate who sent it", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED", liveCopilot: true, mayChangeCopilot: false })],
    });

    const copilot = await screen.findByRole("switch", { name: "Live copilot" });
    expect(copilot).toHaveAttribute("aria-checked", "true");
    expect(copilot).toBeDisabled();
    await userEvent.hover(copilot);
    expect(screen.getByRole("tooltip")).toHaveTextContent("Only whoever sent the Notetaker can change the live copilot.");
  });

  it("opens the copilot in its own window during the call, and in the drawer's tab where no window opens", async () => {
    vi.mocked(openCopilotWindow).mockResolvedValue("blocked");
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "RECORDING", liveCopilot: true, liveStatus: "LISTENING" })],
    });

    expect(screen.queryByRole("switch", { name: "Live copilot" })).not.toBeInTheDocument();
    expect(await screen.findByRole("switch", { name: "Record with Notetaker" })).toBeChecked();
    expect(screen.getByText("Rec")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Open copilot" }));

    expect(openCopilotWindow).toHaveBeenCalledWith(
      { projectId: "p1", candidateId: "c1", recordingId: "r1" },
      expect.any(Function),
    );
    await waitFor(() => expect(onShowCopilot).toHaveBeenCalledWith("r1"));
    expect(screen.queryByRole("region", { name: "Interview copilot" })).not.toBeInTheDocument();
  });

  it("says in the call that a copilot can no longer be added, and that the Notetaker is still joining", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "JOINING", liveCopilot: false })],
    });

    expect(await screen.findByText("Joining…")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open copilot" })).not.toBeInTheDocument();
    await userEvent.hover(screen.getByText("Copilot off"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent(/can't be added to this call/);
  });

  it("prepares questions for an upcoming call, drafting them the first time it opens", async () => {
    const plan: notetakerApi.InterviewPlan = {
      meetingId: "m1",
      title: "First conversation",
      startsAt: "2026-10-07T06:00:00Z",
      drafted: false,
      savedAt: null,
      savedByName: null,
      competencies: [],
      missing: ["NOTICE"],
      questions: [],
    };
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue(plan);
    vi.mocked(notetakerApi.draftInterviewPlan).mockResolvedValue({
      ...plan,
      drafted: true,
      questions: [
        {
          id: "q1",
          section: "GAP",
          panel: null,
          competency: null,
          gap: "NOTICE",
          text: "What notice would you need to give?",
          listenFor: null,
          why: null,
          included: true,
          source: "AI",
        },
      ],
    });
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Prepare questions" }));

    expect(await screen.findByText("What notice would you need to give?")).toBeInTheDocument();
    expect(notetakerApi.draftInterviewPlan).toHaveBeenCalledTimes(1);
  });

  it("says a call's questions are ready once they are saved", async () => {
    const asked = {
      id: "q1",
      section: "GAP" as const,
      panel: null,
      competency: null,
      gap: "NOTICE" as const,
      text: "What notice would you need to give?",
      listenFor: null,
      why: null,
      included: true,
      source: "AI" as const,
    };
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue({
      meetingId: "m1",
      title: "First conversation",
      startsAt: "2026-10-07T06:00:00Z",
      drafted: true,
      savedAt: "2026-10-05T06:00:00Z",
      savedByName: "Yara",
      competencies: [],
      missing: ["NOTICE"],
      questions: [asked, { ...asked, id: "q2", included: false }],
    });
    renderSection();

    expect(await screen.findByRole("button", { name: /1 question ready/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Prepare questions" })).not.toBeInTheDocument();
  });
});

/** A week from Monday 5 October 2099 in Dubai: Monday to Friday worked 08:00–18:00 (04:00–14:00 UTC). */
function aWeek(overrides: Partial<meetingApi.MeetingCalendar> = {}): meetingApi.MeetingCalendar {
  const from = overrides.from ?? "2099-10-05";
  const [year, month, day] = from.split("-").map(Number);
  const workingHours = [0, 1, 2, 3, 4].map((offset) => {
    const date = new Date(Date.UTC(year, month - 1, day + offset)).toISOString().slice(0, 10);
    return { start: `${date}T04:00:00Z`, end: `${date}T14:00:00Z` };
  });
  return {
    address: "yara@firm.example",
    timeZone: "Asia/Dubai",
    provider: "google",
    from,
    days: 7,
    earliestDate: "2099-10-05",
    latestDate: "2100-04-05",
    busy: [],
    workingHours,
    workingHoursSource: "WORKSPACE",
    minMinutes: 5,
    maxMinutes: 480,
    zoomOffered: false,
    ...overrides,
  };
}

/** Types a start into "Pick a time" on the week's first day, the keyboard's way in; the length is the dialog's. */
function pickTime(start: string) {
  fireEvent.change(screen.getByLabelText("Call starts"), { target: { value: start } });
}

function aRecording(overrides: Partial<NotetakerRecording> = {}): NotetakerRecording {
  return {
    id: "r1",
    meetingId: "m1",
    startsAt: "2026-10-07T06:00:00Z",
    title: "Confidential: first conversation",
    status: "SCHEDULED",
    failureReason: null,
    hasTranscript: false,
    retryable: false,
    noteId: null,
    suggestionsAsked: false,
    pendingSuggestions: 0,
    appliedSuggestions: 0,
    liveCopilot: false,
    liveStatus: null,
    mayChangeCopilot: true,
    ...overrides,
  };
}
