import { request } from "../../../lib/apiClient";

/**
 * An executive's meetings on the team's calendars, and Book a call from the consultant's own. The
 * position's `WORK_EXECUTE`: a client seat sees none of a firm's diaries.
 */

export const MEETINGS_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId, "meetings"] as const;

export const MEETING_SLOTS_KEY = (projectId: string, candidateId: string, minutes: number, from: string | null) =>
  ["outreach", projectId, "candidate", candidateId, "slots", minutes, from] as const;

export type MeetingVideo = "GOOGLE_MEET" | "MICROSOFT_TEAMS" | "NONE";

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
}

export interface PersonMeetings {
  upcoming: Meeting[];
  past: Meeting[];
}

export interface SlotDay {
  /** The day in the consultant's own zone, `YYYY-MM-DD`. */
  date: string;
  /** Free starts; none means the day is fully booked. */
  starts: string[];
}

export interface MeetingSlots {
  address: string;
  timeZone: string;
  /** The mailbox's host, which decides the video link offered first. */
  provider: string;
  minutes: number;
  /** Today in the consultant's zone, `YYYY-MM-DD`: the grid pages no earlier. */
  earliestDate: string;
  /** The furthest day the grid pages to, `YYYY-MM-DD`. */
  latestDate: string;
  /** Where the page before this one starts, a working week back; null on the first page. */
  previousFrom: string | null;
  days: SlotDay[];
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

export function getMeetingSlots(
  projectId: string,
  candidateId: string,
  minutes: number,
  from: string | null,
  signal?: AbortSignal,
): Promise<MeetingSlots> {
  const query = new URLSearchParams({ minutes: String(minutes) });
  if (from) query.set("from", from);
  return request<MeetingSlots>(`${meetingsPath(projectId, candidateId)}/slots?${query}`, { signal });
}

export function bookMeeting(projectId: string, candidateId: string, body: BookMeetingRequest): Promise<void> {
  return request<void>(meetingsPath(projectId, candidateId), { method: "POST", body });
}
