import { useQuery } from "@tanstack/react-query";
import type { NotetakerRecording } from "../api/notetakerApi";
import * as notetakerApi from "../api/notetakerApi";
import { hasLiveRecording, hasRecordingAboutToMove } from "./notetakerChip";

/** One executive's Notetaker on a position, read once for the Meetings section and the drawer's copilot tab alike. */
export function useNotetaker(projectId: string, candidateId: string, enabled = true) {
  return useQuery({
    queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId),
    queryFn: ({ signal }) => notetakerApi.getNotetaker(projectId, candidateId, signal),
    enabled,
    // A bot on its way, in a call or being written up moves on Recall's word, which no stream carries here.
    refetchInterval: (query) => {
      const recordings = query.state.data?.recordings ?? [];
      if (hasRecordingAboutToMove(recordings)) return 10_000;
      return hasLiveRecording(recordings) ? 30_000 : false;
    },
  });
}

/**
 * The call whose live copilot is running: chosen, its bot joining or in the call, and neither stopped nor dropped.
 * The drawer's Copilot tab is offered while there is one.
 */
export function liveCopilotOf(recordings: readonly NotetakerRecording[]): NotetakerRecording | null {
  return (
    recordings.find(
      (recording) =>
        recording.liveCopilot &&
        (recording.status === "JOINING" || recording.status === "RECORDING") &&
        (recording.liveStatus === null || recording.liveStatus === "LISTENING"),
    ) ?? null
  );
}
