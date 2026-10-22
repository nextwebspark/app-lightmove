import { useQuery } from "@tanstack/react-query";
import * as notetakerApi from "../api/notetakerApi";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerStatus } from "../api/notetakerApi";
import { joinsLineOf } from "./notetakerChip";

/** A bot on its way in, in the call or writing its note: worth reading again while the stream is quiet. */
const MOVING: readonly NotetakerStatus[] = ["LAUNCHING", "WAITING_ROOM", "RECORDING", "REMOVE_REQUESTED", "PROCESSING"];
const MOVING_REFRESH_MS = 20_000;
/** A call starting this soon is about to get its bot (the scheduler sends one two minutes ahead), so read it too. */
const ABOUT_TO_START_MS = 5 * 60_000;

/**
 * Uncava Notetaker on an executive's meetings (`Position.dc.html`'s Meetings): the per-call switch, where each bot
 * has got to, Remove notetaker, Try again and the transcript. The `notetaker` stream kind refreshes it; while a bot
 * is moving it also reads again on its own, for the stretch the stream is down.
 */
export function useNotetaker(projectId: string, candidateId: string, upcoming: readonly Meeting[]) {
  const meetings = useQuery({
    queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => notetakerApi.getNotetakerMeetings(projectId, candidateId, signal),
    refetchInterval: (query) => {
      const calls = query.state.data?.meetings ?? [];
      const moving = calls.some((call) => call.status !== null && MOVING.includes(call.status));
      const aboutToStart = calls.some(
        (call) => call.joinable && call.status === null && startsSoon(upcoming.find((row) => row.id === call.meetingId)),
      );
      return moving || aboutToStart ? MOVING_REFRESH_MS : false;
    },
  });
  const workspace = useQuery({
    queryKey: notetakerApi.WORKSPACE_NOTETAKER_KEY,
    queryFn: ({ signal }) => notetakerApi.getWorkspaceNotetaker(signal),
    enabled: meetings.data?.offered === true,
  });
  const byMeeting = new Map((meetings.data?.meetings ?? []).map((meeting) => [meeting.meetingId, meeting]));
  return {
    data: meetings.data?.offered ? meetings.data : null,
    of: (meetingId: string) => byMeeting.get(meetingId) ?? null,
    joinsLine: joinsLineOf(workspace.data?.platforms ?? []),
  };
}


function startsSoon(meeting: Meeting | undefined): boolean {
  if (!meeting) return false;
  const now = Date.now();
  return new Date(meeting.startsAt).getTime() - now < ABOUT_TO_START_MS && new Date(meeting.endsAt).getTime() > now;
}
