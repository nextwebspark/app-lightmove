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
  joinNotetakerNow: vi.fn(),
  endNotetaker: vi.fn(),
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

/** The same call, on a day still to come. */
const LATER: PersonMeetings = {
  ...MEETINGS,
  upcoming: [{ ...MEETINGS.upcoming[0], startsAt: "2099-10-07T06:00:00Z", endsAt: "2099-10-07T06:30:00Z" }],
};

function renderSection({
  meetings = MEETINGS,
  doNotContact = false,
  offered = true,
  zoomStatus = null,
  notetakerOffered = false,
  recordings = [],
  recordableMeetingIds = ["m1"],
  startableMeetingIds = ["m1"],
}: {
  meetings?: PersonMeetings;
  doNotContact?: boolean;
  offered?: boolean;
  zoomStatus?: zoomApi.ZoomAccount["status"];
  notetakerOffered?: boolean;
  recordings?: NotetakerRecording[];
  recordableMeetingIds?: string[];
  startableMeetingIds?: string[];
} = {}) {
  vi.mocked(meetingApi.getMeetings).mockResolvedValue(meetings);
  vi.mocked(notetakerApi.getNotetaker).mockResolvedValue({
    offered: notetakerOffered,
    recordings,
    recordableMeetingIds: notetakerOffered && !doNotContact ? recordableMeetingIds : [],
    startableMeetingIds: notetakerOffered && !doNotContact ? startableMeetingIds : [],
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
  return render(
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
    // The call's panel reads its plan; left unanswered, that read settles after the test has ended.
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

    await openMenuItem("Reschedule");
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

    const [booked, found] = await screen.findAllByRole("button", { name: "Call options" });
    await userEvent.click(found);
    expect(screen.queryByRole("menuitem", { name: "Cancel call" })).not.toBeInTheDocument();
    await userEvent.click(found);
    await userEvent.click(booked);
    await userEvent.click(screen.getByRole("menuitem", { name: "Cancel call" }));
    expect(await screen.findByText("Cancel the call with Priya Raman?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Keep call" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancel call" }));

    await waitFor(() => expect(meetingApi.cancelMeeting).toHaveBeenCalledWith("p1", "c1", "m1"));
    expect(await screen.findByText("Call with Priya cancelled. They've been told.")).toBeInTheDocument();
  });

  it("offers no Reschedule for someone marked do not contact, though the call can still be cancelled", async () => {
    renderSection({ doNotContact: true });

    // Absence proves nothing until the person record, which decides it, has landed.
    await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
    await vi.mocked(poolApi.getPerson).mock.results[0].value;
    await userEvent.click(await screen.findByRole("button", { name: "Call options" }));
    expect(await screen.findByRole("menuitem", { name: "Cancel call" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Reschedule" })).not.toBeInTheDocument();
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

  it("keeps a call's row to the call, its link and its ⋯, and a call found on a calendar to its date and title", async () => {
    renderSection({ notetakerOffered: true });

    expect(await screen.findByRole("button", { name: "Call options" })).toBeInTheDocument();
    await waitFor(() => expect(notetakerApi.getNotetaker).toHaveBeenCalled());
    await vi.mocked(notetakerApi.getNotetaker).mock.results[0].value;
    expect(screen.queryByRole("button", { name: /Start now|Join at start|Prepare questions|Notetaker/ })).toBeNull();
    expect(screen.getAllByRole("button", { name: /^Call options/ })).toHaveLength(1);
    expect(notetakerApi.getInterviewPlan).not.toHaveBeenCalled();
  });

  it("starts the Notetaker now from the call's panel, with the copilot chosen first", async () => {
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(
      aRecording({ status: "SCHEDULED", joinAt: null, liveCopilot: true }),
    );
    renderSection({ notetakerOffered: true });

    await openMenuItem("Notetaker & questions…");
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.getByText("Off")).toBeInTheDocument();
    await userEvent.click(panel.getByRole("switch", { name: "Live copilot" }));
    expect(notetakerApi.scheduleNotetaker).not.toHaveBeenCalled();
    await userEvent.click(panel.getByRole("button", { name: "Start now" }));

    expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m1", true, "NOW");
    expect(await screen.findByText(/The Notetaker is joining the call with Priya, and the live copilot/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Notetaker & questions" })).toBeNull());
  });

  it("schedules the Notetaker for the start of a call on a later day, Start now opening on its day", async () => {
    vi.mocked(notetakerApi.scheduleNotetaker).mockResolvedValue(aRecording({ status: "SCHEDULED" }));
    renderSection({ meetings: LATER, notetakerOffered: true, startableMeetingIds: [] });

    await openMenuItem("Notetaker & questions…");
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.getByText("Start now opens on the day of the call.")).toBeInTheDocument();
    expect(panel.queryByRole("button", { name: "Start now" })).toBeNull();
    await userEvent.click(panel.getByRole("button", { name: /Join at start/ }));

    expect(notetakerApi.scheduleNotetaker).toHaveBeenCalledWith("p1", "c1", "m1", false);
    expect(await screen.findByText(/The Notetaker will join the call with Priya at its start/)).toBeInTheDocument();
  });

  it("offers only questions where no Notetaker is set up, on a teammate's call, or for someone marked do not contact", async () => {
    for (const setup of [
      { notetakerOffered: false },
      { notetakerOffered: true, recordableMeetingIds: [] },
      { notetakerOffered: true, doNotContact: true },
    ]) {
      const { unmount } = renderSection(setup);
      await waitFor(() => expect(poolApi.getPerson).toHaveBeenCalled());
      await waitFor(() => expect(notetakerApi.getNotetaker).toHaveBeenCalled());
      await vi.mocked(poolApi.getPerson).mock.results[0].value;
      await userEvent.click(await screen.findByRole("button", { name: "Call options" }));

      expect(await screen.findByRole("menuitem", { name: "Prepare questions…" })).toBeInTheDocument();
      expect(screen.queryByRole("menuitem", { name: "Notetaker & questions…" })).toBeNull();
      unmount();
      vi.mocked(poolApi.getPerson).mockClear();
      vi.mocked(notetakerApi.getNotetaker).mockClear();
    }
  });

  it("says in the call's ⋯ what is set for it, and changes it from its panel", async () => {
    vi.mocked(notetakerApi.switchLiveCopilot).mockResolvedValue(aRecording({ liveCopilot: false }));
    renderSection({
      meetings: LATER,
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED", joinAt: "2099-10-07T05:59:00Z", liveCopilot: true })],
    });

    await userEvent.click(await screen.findByRole("button", { name: "Call options" }));
    expect(await screen.findByText("Notetaker joins at start", { exact: false })).toHaveTextContent(
      "Notetaker joins at start · Live copilot",
    );
    await userEvent.click(await screen.findByRole("menuitem", { name: "Notetaker & questions…" }));
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.getByText("Joins at start")).toBeInTheDocument();
    const copilot = panel.getByRole("switch", { name: "Live copilot" });
    expect(copilot).toHaveAttribute("aria-checked", "true");
    await userEvent.click(copilot);

    expect(notetakerApi.switchLiveCopilot).toHaveBeenCalledWith("p1", "c1", "r1", false);
  });

  it("leaves a teammate's copilot to the teammate who sent the Notetaker", async () => {
    renderSection({
      meetings: LATER,
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED", liveCopilot: true, mayChangeCopilot: false })],
    });

    await openMenuItem("Notetaker & questions…");
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.getByRole("switch", { name: "Live copilot" })).toBeDisabled();
    expect(panel.getByText("Only whoever sent the Notetaker can change it.")).toBeInTheDocument();
  });

  it("removes a scheduled Notetaker from its panel after asking", async () => {
    vi.mocked(notetakerApi.cancelNotetaker).mockResolvedValue(undefined);
    renderSection({
      meetings: LATER,
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED", joinAt: "2099-10-07T05:59:00Z" })],
    });

    await openMenuItem("Notetaker & questions…");
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "Remove the Notetaker?" })).getByRole("button", {
        name: "Remove notetaker",
      }),
    );

    expect(notetakerApi.cancelNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
  });

  it("brings a Notetaker scheduled for the start in now, once the call's day has begun", async () => {
    vi.mocked(notetakerApi.joinNotetakerNow).mockResolvedValue(aRecording({ status: "SCHEDULED", joinAt: null }));
    renderSection({
      meetings: LATER,
      notetakerOffered: true,
      recordings: [aRecording({ status: "SCHEDULED", joinAt: "2099-10-07T05:59:00Z" })],
    });

    await openMenuItem("Notetaker & questions…");
    await userEvent.click(screen.getByRole("button", { name: "Start now instead" }));

    expect(notetakerApi.joinNotetakerNow).toHaveBeenCalledWith("p1", "c1", "r1");
    expect(await screen.findByText(/The Notetaker is joining the call with Priya now/)).toBeInTheDocument();
  });

  it("offers no Start now instead before the call's day", async () => {
    renderSection({
      meetings: LATER,
      notetakerOffered: true,
      startableMeetingIds: [],
      recordings: [aRecording({ status: "SCHEDULED", joinAt: "2099-10-07T05:59:00Z" })],
    });

    await openMenuItem("Notetaker & questions…");
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.getByText("Start now opens on the day of the call.")).toBeInTheDocument();
    expect(panel.queryByRole("button", { name: "Start now instead" })).toBeNull();
  });

  it("says a Notetaker on its way in is joining, with its copilot as it was sent", async () => {
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "SCHEDULED", joinAt: null })] });

    await userEvent.click(await screen.findByRole("button", { name: "Call options" }));
    expect(await screen.findByText("Notetaker joining", { exact: false })).toHaveTextContent(
      "Notetaker joining · let it in if it waits in the lobby",
    );
    await userEvent.click(await screen.findByRole("menuitem", { name: "Notetaker & questions…" }));
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(panel.queryByRole("switch", { name: "Live copilot" })).toBeNull();
    expect(panel.getByText(/Live copilot is off for this call/)).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Remove" })).toBeInTheDocument();
  });

  it("marks a call being recorded on its ⋯ alone, and ends it from its panel once asked", async () => {
    vi.mocked(notetakerApi.endNotetaker).mockResolvedValue(aRecording({ status: "RECORDING", endRequested: true }));
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "RECORDING" })] });

    await userEvent.click(await screen.findByRole("button", { name: "Call options · recording" }));
    expect(screen.queryByRole("button", { name: "End & file notes" })).toBeNull();
    expect(screen.getByText(/^Recording/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("menuitem", { name: "Notetaker & questions…" }));
    await userEvent.click(screen.getByRole("button", { name: "End & file notes" }));
    const dialog = within(screen.getByRole("dialog", { name: "End the recording and file the notes?" }));
    expect(dialog.getByText(/can't rejoin this call/)).toBeInTheDocument();
    await userEvent.click(dialog.getByRole("button", { name: "End & file notes" }));

    expect(notetakerApi.endNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
    expect(await screen.findByText(/The Notetaker is leaving the call/)).toBeInTheDocument();
    expect(notetakerApi.cancelNotetaker).not.toHaveBeenCalled();
  });

  it("opens the copilot in its own window during the call, and in the drawer's tab where no window opens", async () => {
    vi.mocked(openCopilotWindow).mockResolvedValue("blocked");
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "RECORDING", liveCopilot: true, liveStatus: "LISTENING" })],
    });

    await openMenuItem("Notetaker & questions…");
    await userEvent.click(screen.getByRole("button", { name: "Open copilot" }));

    expect(openCopilotWindow).toHaveBeenCalledWith(
      { projectId: "p1", candidateId: "c1", recordingId: "r1" },
      expect.any(Function),
    );
    await waitFor(() => expect(onShowCopilot).toHaveBeenCalledWith("r1"));
  });

  it("discards a recording only from the call's panel, after saying nothing will be filed", async () => {
    vi.mocked(notetakerApi.cancelNotetaker).mockResolvedValue(undefined);
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "RECORDING" })] });

    await openMenuItem("Notetaker & questions…");
    await userEvent.click(screen.getByRole("button", { name: "Remove and discard" }));
    const dialog = within(screen.getByRole("dialog", { name: "Remove and discard?" }));
    expect(dialog.getByText(/nothing it recorded is kept or filed/)).toBeInTheDocument();
    await userEvent.click(dialog.getByRole("button", { name: "Remove and discard" }));

    expect(notetakerApi.cancelNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
  });

  it("keeps a call recorded past its booked end ended from its panel, through the ⋯ of its past row", async () => {
    vi.mocked(notetakerApi.endNotetaker).mockResolvedValue(aRecording({ status: "RECORDING", endRequested: true }));
    renderSection({
      meetings: { upcoming: [], past: [MEETINGS.upcoming[0]] },
      notetakerOffered: true,
      recordableMeetingIds: [],
      recordings: [aRecording({ status: "RECORDING" })],
    });

    await openCallMenu("Confidential: first conversation", "Call options · recording");
    await userEvent.click(screen.getByRole("menuitem", { name: "Notetaker…" }));
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "Notetaker & questions" })).getByRole("button", {
        name: "End & file notes",
      }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "End the recording and file the notes?" })).getByRole("button", {
        name: "End & file notes",
      }),
    );

    expect(notetakerApi.endNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
  });

  it("moves a call the Notetaker has left to Past, its notes on their way", async () => {
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "RECORDING", endRequested: true })] });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    expect(screen.getByText(/the notes follow in a minute or two/)).toBeInTheDocument();
    expect(screen.queryByText("Upcoming")).toBeNull();
    expect(screen.getByText("Past")).toBeInTheDocument();
    expect(screen.queryByRole("menuitem")).toBeNull();
  });

  it("writes the notes, then finds the changes, with nothing to press", async () => {
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "PROCESSING" })] });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    expect(screen.getByText("Writing notes…")).toBeInTheDocument();
  });

  it("offers the copilot from the ⋯ while the notes are written", async () => {
    vi.mocked(openCopilotWindow).mockResolvedValue("floating");
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "PROCESSING", liveCopilot: true })] });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    await userEvent.click(screen.getByRole("menuitem", { name: "Open copilot" }));

    expect(openCopilotWindow).toHaveBeenCalledWith(
      { projectId: "p1", candidateId: "c1", recordingId: "r1" },
      expect.any(Function),
    );
  });

  it("says the changes are being found once the notes are filed", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "DONE", hasTranscript: true, suggesting: true })],
    });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    expect(screen.getByText(/finding changes…/)).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Review|Suggest changes|Changes/ })).toBeNull();
  });

  it("marks a call whose changes wait on its ⋯, and opens the review from it", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue({
      recordingId: "r1",
      title: "Confidential: first conversation",
      startsAt: "2026-10-07T06:00:00Z",
      asked: true,
      mayAskAgain: true,
      suggestions: [],
    });
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "DONE", hasTranscript: true, suggestionsAsked: true, pendingSuggestions: 2, foundSuggestions: 2 })],
    });

    await openCallMenu("Confidential: first conversation", "Call options · 2 changes to review");
    expect(screen.getByText(/2 changes to review/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("menuitem", { name: "Review 2 changes" }));

    expect(await screen.findByRole("dialog", { name: "Changes from the call" })).toBeInTheDocument();
    expect(notetakerApi.getCallSuggestions).toHaveBeenCalledWith("p1", "c1", "r1", expect.anything());
  });

  it("keeps what a review changed in a past call's ⋯", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue({
      recordingId: "r2",
      title: "Coffee",
      startsAt: "2026-09-22T06:00:00Z",
      asked: true,
      mayAskAgain: true,
      suggestions: [
        {
          id: "s1",
          kind: "NOTICE_PERIOD",
          status: "APPLIED",
          value: { ...NO_VALUE, text: "1 month" },
          current: { ...NO_VALUE, text: "1 month" },
          said: "I could start within a month.",
          at: "12:40",
          available: true,
          edited: false,
          was: { ...NO_VALUE, text: "3 months" },
          appliedAt: "2026-09-22T07:00:00Z",
          appliedBy: "Yara Haddad",
        },
      ],
    });
    renderSection({
      notetakerOffered: true,
      recordings: [
        aRecording({
          id: "r2",
          meetingId: "m2",
          startsAt: "2026-09-22T06:00:00Z",
          title: "Coffee",
          status: "DONE",
          hasTranscript: true,
          suggestionsAsked: true,
          appliedSuggestions: 1,
          foundSuggestions: 2,
        }),
        aRecording({ status: "DONE", hasTranscript: true, suggestionsAsked: true }),
      ],
    });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    expect(screen.getByText(/no changes found/)).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Changes from the call" })).toBeNull();
    await userEvent.keyboard("{Escape}");

    await openCallMenu("Coffee", "Call options");
    expect(screen.getByText(/1 change applied/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("menuitem", { name: "Changes from the call" }));

    const dialog = within(await screen.findByRole("dialog", { name: "Changes from the call" }));
    expect(await dialog.findByText("Applied by Yara")).toBeInTheDocument();
    expect(dialog.getByText("3 months").tagName).toBe("DEL");
    expect(dialog.getByText("I could start within a month.")).toBeInTheDocument();
  });

  it("offers Suggest changes on a call whose changes were never found", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue({
      recordingId: "r1",
      title: "Confidential: first conversation",
      startsAt: "2026-10-07T06:00:00Z",
      asked: true,
      mayAskAgain: true,
      suggestions: [],
    });
    renderSection({ notetakerOffered: true, recordings: [aRecording({ status: "DONE", hasTranscript: true })] });

    await untilMovedToPast();
    await openCallMenu("Confidential: first conversation", "Call options");
    await userEvent.click(screen.getByRole("menuitem", { name: "Suggest changes" }));

    expect(await screen.findByText("Nothing to change")).toBeInTheDocument();
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

    await openCallMenu("Coffee", "Call options");
    expect(screen.getByText("Notes filed")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("menuitem", { name: "Transcript" }));

    expect(await screen.findByText("Happy to talk.")).toBeInTheDocument();
    expect(screen.getByText("01:05")).toBeInTheDocument();
  });

  it("offers neither the changes nor the transcript once the transcript is no longer kept", async () => {
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ id: "r2", meetingId: "m2", startsAt: "2026-09-22T06:00:00Z", status: "DONE" })],
    });

    await openCallMenu("Coffee", "Call options");
    expect(screen.getByText("Notes filed")).toBeInTheDocument();
    expect(screen.queryByRole("menuitem")).toBeNull();
  });

  it("says why no notes were filed, and tries again from the ⋯ only where the call was heard", async () => {
    vi.mocked(notetakerApi.retryNotetaker).mockResolvedValue(aRecording({ status: "PROCESSING" }));
    renderSection({
      notetakerOffered: true,
      recordings: [aRecording({ status: "FAILED", failureReason: "SUMMARY_FAILED", retryable: true })],
    });

    await openCallMenu("Confidential: first conversation", "Call options · no notes");
    expect(screen.getByText(/No notes/)).toHaveTextContent("No notes · the notes couldn't be written");
    await userEvent.click(screen.getByRole("menuitem", { name: "Try again" }));
    expect(notetakerApi.retryNotetaker).toHaveBeenCalledWith("p1", "c1", "r1");
  });

  it("says why a call was not recorded, with nothing left to try", async () => {
    renderSection({
      notetakerOffered: true,
      recordableMeetingIds: [],
      recordings: [aRecording({ status: "FAILED", failureReason: "timeout_exceeded_waiting_room" })],
    });

    await openCallMenu("Confidential: first conversation", "Call options");
    expect(await screen.findByText(/Not recorded/)).toHaveTextContent("Not recorded · nobody let it in");
    expect(screen.queryByRole("menuitem", { name: "Try again" })).toBeNull();
  });

  it("prepares questions from the call's panel, which then says they are ready without a save", async () => {
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
    const drafted = { ...plan, drafted: true, questions: [{ ...A_QUESTION, included: true }] };
    vi.mocked(notetakerApi.getInterviewPlan).mockImplementation(async () =>
      vi.mocked(notetakerApi.draftInterviewPlan).mock.calls.length > 0 ? drafted : plan,
    );
    vi.mocked(notetakerApi.draftInterviewPlan).mockResolvedValue(drafted);
    renderSection({ notetakerOffered: true });

    await openMenuItem("Notetaker & questions…");
    await userEvent.click(screen.getByRole("button", { name: "Prepare questions" }));

    expect(await screen.findByText("What notice would you need to give?")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Notetaker & questions" })).toBeNull();
    expect(notetakerApi.draftInterviewPlan).toHaveBeenCalledTimes(1);
    await userEvent.click(within(screen.getByRole("dialog", { name: "Interview plan" })).getByRole("button", { name: "Close" }));

    await openMenuItem("Notetaker & questions…");
    const panel = within(screen.getByRole("dialog", { name: "Notetaker & questions" }));
    expect(await panel.findByText("1 ready")).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Open questions" })).toBeInTheDocument();
    expect(panel.queryByRole("button", { name: "Prepare questions" })).toBeNull();
  });

  it("books a call with nothing about the Notetaker in it", async () => {
    vi.mocked(meetingApi.getMeetingCalendar).mockResolvedValue(aWeek());
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue({ meetingId: "m9" });
    renderSection({ notetakerOffered: true });

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("group", { name: "Your week" });
    expect(within(screen.getByRole("dialog")).queryByRole("switch")).toBeNull();
    pickTime("10:00");
    await userEvent.click(screen.getByRole("button", { name: "Send invite" }));

    await waitFor(() => expect(meetingApi.bookMeeting).toHaveBeenCalled());
    expect(notetakerApi.scheduleNotetaker).not.toHaveBeenCalled();
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

/** Opens the only call's ⋯ and picks one of its items. */
async function openMenuItem(name: string) {
  await userEvent.click(await screen.findByRole("button", { name: /^Call options/ }));
  await userEvent.click(await screen.findByRole("menuitem", { name }));
}

/** Opens the ⋯ on the row of the call titled so, by the name its dot gives it; the row is read again as the call moves. */
async function openCallMenu(title: string, name: string) {
  const trigger = await waitFor(() =>
    within(screen.getByText(title).closest("div") as HTMLElement).getByRole("button", { name }),
  );
  await userEvent.click(trigger);
}

/** Until the Notetaker is read, the only call is upcoming; one it has left then moves to Past. */
async function untilMovedToPast() {
  await screen.findByText("Coffee");
  await waitFor(() => expect(screen.queryByText("Upcoming")).toBeNull());
}

const NO_VALUE: notetakerApi.SuggestedValue = {
  text: null,
  city: null,
  country: null,
  currency: null,
  baseSalary: null,
  bonus: null,
  allowances: null,
  working: null,
};

const A_QUESTION: notetakerApi.InterviewQuestion = {
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
};

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
    foundSuggestions: 0,
    suggesting: false,
    liveCopilot: false,
    liveStatus: null,
    mayChangeCopilot: true,
    joinAt: "2026-10-07T05:59:00Z",
    recordingStartedAt: null,
    endRequested: false,
    ...overrides,
  };
}
