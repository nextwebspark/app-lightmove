import { request } from "../../../lib/apiClient";

/**
 * An executive's meetings on the team's calendars, and Book a call from the consultant's own. The
 * position's `WORK_EXECUTE`: a client seat sees none of a firm's diaries.
 */

export const MEETINGS_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId, "meetings"] as const;

export const MEETING_CALENDAR_KEY = (
  projectId: string,
  candidateId: string,
  from: string | null,
  excluding: string | null = null,
) => ["outreach", projectId, "candidate", candidateId, "calendar", from, excluding] as const;

export type MeetingVideo = "GOOGLE_MEET" | "MICROSOFT_TEAMS" | "ZOOM" | "NONE";

export interface Meeting {
  id: string;
  title: string | null;
  startsAt: string;
  endsAt: string;
  ownerUserId: string;
  ownerName: string | null;
  /** Only on a meeting still to come. */
  joinUrl: string | null;
  /** The calendar's own name for the video link, e.g. "Google Meet". */
  videoProvider: string | null;
  viaLink: boolean;
  bookedInUncava: boolean;
  /** Booked in Uncava, still to come, and on the caller's own calendar: they may move or cancel it. */
  editable: boolean;
}

export interface PersonMeetings {
  upcoming: Meeting[];
  past: Meeting[];
}

/** A day of free times on a booking link's page; Uncava's own page, `bookingApi`, reads these. */
export interface SlotDay {
  /** The day in the consultant's own zone, `YYYY-MM-DD`. */
  date: string;
  /** Free starts; none means the day is fully booked. */
  starts: string[];
}

export interface TimeSpan {
  start: string;
  end: string;
}

/** Where a consultant's working hours came from: Outlook's own, theirs, or the workspace's. */
export type WorkingHoursSource = "CALENDAR" | "PERSONAL" | "WORKSPACE";

/** Book a call's week: the consultant's calendar as free/busy, and their working hours, which only label a time. */
export interface MeetingCalendar {
  address: string;
  timeZone: string;
  /** The mailbox's host, which decides the video link offered first. */
  provider: string;
  /** The first day shown, `YYYY-MM-DD`, in the consultant's zone. */
  from: string;
  days: number;
  /** Today in the consultant's zone: the view pages no earlier. */
  earliestDate: string;
  /** The furthest day a call may be booked on. */
  latestDate: string;
  busy: TimeSpan[];
  workingHours: TimeSpan[];
  workingHoursSource: WorkingHoursSource;
  minMinutes: number;
  maxMinutes: number;
  /** The consultant's own Zoom account can put a Zoom link on the invite. */
  zoomOffered: boolean;
}

export interface BookMeetingRequest {
  startsAt: string;
  minutes: number;
  video: MeetingVideo;
  inviteAddress: string;
  title: string;
}

function meetingsPath(projectId: string, candidateId: string): string {
  return `/projects/${projectId}/outreach/candidates/${candidateId}/meetings`;
}

export function getMeetings(projectId: string, candidateId: string, signal?: AbortSignal): Promise<PersonMeetings> {
  return request<PersonMeetings>(meetingsPath(projectId, candidateId), { signal });
}

export function getMeetingCalendar(
  projectId: string,
  candidateId: string,
  from: string | null,
  excluding: string | null,
  signal?: AbortSignal,
): Promise<MeetingCalendar> {
  const query = new URLSearchParams();
  if (from) query.set("from", from);
  // The call being moved: its own time reads as free, so it can move within it.
  if (excluding) query.set("excluding", excluding);
  return request<MeetingCalendar>(`${meetingsPath(projectId, candidateId)}/calendar?${query}`, { signal });
}

export interface BookedMeeting {
  meetingId: string;
}

export function bookMeeting(projectId: string, candidateId: string, body: BookMeetingRequest): Promise<BookedMeeting> {
  return request<BookedMeeting>(meetingsPath(projectId, candidateId), { method: "POST", body });
}

export function rescheduleMeeting(
  projectId: string,
  candidateId: string,
  meetingId: string,
  body: { startsAt: string; minutes: number },
): Promise<void> {
  return request<void>(`${meetingsPath(projectId, candidateId)}/${meetingId}`, { method: "PUT", body });
}

export function cancelMeeting(projectId: string, candidateId: string, meetingId: string): Promise<void> {
  return request<void>(`${meetingsPath(projectId, candidateId)}/${meetingId}`, { method: "DELETE" });
}
