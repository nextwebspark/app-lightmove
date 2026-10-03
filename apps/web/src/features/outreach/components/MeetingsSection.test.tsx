import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateEmail, PersonRecord } from "../../candidates/api/types";
import * as mailboxApi from "../api/mailboxApi";
import * as meetingApi from "../api/meetingApi";
import * as zoomApi from "../api/zoomApi";
import type { PersonMeetings } from "../api/meetingApi";
import { MeetingsSection } from "./MeetingsSection";

vi.mock("../api/meetingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/meetingApi")>()),
  getMeetings: vi.fn(),
  getMeetingSlots: vi.fn(),
  bookMeeting: vi.fn(),
}));
vi.mock("../api/zoomApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/zoomApi")>()),
  getZoom: vi.fn(),
}));
vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));
vi.mock("../../candidates/api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/poolApi")>()),
  getPerson: vi.fn(),
}));

const EMAILS: CandidateEmail[] = [
  { address: "priya@target.example", kind: "work", verified: true, status: null, source: "manual", foundAt: "2026-09-01T00:00:00Z" },
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
    },
  ],
};

function renderSection({
  meetings = MEETINGS,
  doNotContact = false,
  offered = true,
  zoomStatus = null,
}: {
  meetings?: PersonMeetings;
  doNotContact?: boolean;
  offered?: boolean;
  zoomStatus?: zoomApi.ZoomAccount["status"];
} = {}) {
  vi.mocked(meetingApi.getMeetings).mockResolvedValue(meetings);
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
      connectedAt: "2026-09-01T00:00:00Z",
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
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("MeetingsSection", () => {
  beforeEach(() => vi.resetAllMocks());

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

  it("books a picked time from the consultant's calendar", async () => {
    vi.mocked(meetingApi.getMeetingSlots).mockResolvedValue({
      address: "yara@firm.example",
      timeZone: "Asia/Dubai",
      provider: "google",
      minutes: 30,
      zoomOffered: false,
      days: [
        { date: "2026-10-05", starts: ["2026-10-05T06:00:00Z", "2026-10-05T06:30:00Z"] },
        { date: "2026-10-06", starts: [] },
      ],
    });
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue(undefined);
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    expect(await screen.findByText("Fully booked")).toBeInTheDocument();
    const send = screen.getByRole("button", { name: "Send invite" });
    expect(send).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "10:30" }));
    expect(screen.getByText("Mon 5 Oct · 10:30 · 30 min · invite from yara@firm.example")).toBeInTheDocument();
    await userEvent.click(send);

    await waitFor(() =>
      expect(meetingApi.bookMeeting).toHaveBeenCalledWith("p1", "c1", {
        startsAt: "2026-10-05T06:30:00Z",
        minutes: 30,
        video: "GOOGLE_MEET",
        inviteAddress: "priya@target.example",
        title: "Confidential: first conversation",
      }),
    );
    expect(await screen.findByText(/Invite sent to Priya for Mon 5 Oct, 10:30\. They moved to Engaged\./)).toBeInTheDocument();
  });

  it("offers only the video link the connected calendar can make", async () => {
    vi.mocked(meetingApi.getMeetingSlots).mockResolvedValue({
      address: "yara@firm.example",
      timeZone: "Asia/Dubai",
      provider: "google",
      minutes: 30,
      zoomOffered: false,
      days: [{ date: "2026-10-05", starts: ["2026-10-05T06:00:00Z"] }],
    });
    renderSection();

    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await screen.findByRole("button", { name: "10:00" });
    const video = screen.getByRole("combobox", { name: "Video link" });
    expect(Array.from(video.querySelectorAll("option"), (option) => option.textContent)).toEqual([
      "Google Meet",
      "No video link",
    ]);
  });

  it("offers Zoom in Book a call only where the consultant's own Zoom account is connected", async () => {
    vi.mocked(meetingApi.getMeetingSlots).mockResolvedValue({
      address: "yara@firm.example",
      timeZone: "Asia/Dubai",
      provider: "google",
      minutes: 30,
      zoomOffered: true,
      days: [{ date: "2026-10-05", starts: ["2026-10-05T06:00:00Z"] }],
    });
    vi.mocked(meetingApi.bookMeeting).mockResolvedValue(undefined);
    renderSection({ zoomStatus: "ACTIVE" });

    expect(await screen.findByText("Zoom connected")).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Book a call" }));
    await userEvent.click(await screen.findByRole("button", { name: "10:00" }));
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
});
