import { useQuery } from "@tanstack/react-query";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerStatus } from "../api/notetakerApi";
import { joinsLineOf } from "./notetakerChip";

/** A bot on its way in, in the call or writing its note: worth reading again while the stream is quiet. */
const MOVING: readonly NotetakerStatus[] = ["LAUNCHING", "WAITING_ROOM", "RECORDING", "REMOVE_REQUESTED", "PROCESSING"];
const MOVING_REFRESH_MS = 20_000;

/**
 * Uncava Notetaker on an executive's meetings (`Position.dc.html`'s Meetings): the per-call switch, where each bot
 * has got to, Remove notetaker, Try again and the transcript. The `notetaker` stream kind refreshes it; while a bot
 * is moving it also reads again on its own, for the stretch the stream is down.
 */
export function useNotetaker(projectId: string, candidateId: string) {
  const meetings = useQuery({
    queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => notetakerApi.getNotetakerMeetings(projectId, candidateId, signal),
    refetchInterval: (query) =>
      query.state.data?.meetings.some((meeting) => meeting.status && MOVING.includes(meeting.status))
        ? MOVING_REFRESH_MS
        : false,
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

