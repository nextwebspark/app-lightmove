import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerJoin, NotetakerRecording } from "../api/notetakerApi";

/**
 * What can be done to one executive's Notetaker, shared by a call's row and its Notetaker & questions panel: each
 * press says in a toast what it did, and the section reads its bots again after it, whatever the answer.
 */
export function useNotetakerActions(projectId: string, candidateId: string, firstName: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const refresh = () => queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId) });
  const onError = (error: unknown) => {
    toast.error(messageFor(error));
    void refresh();
  };

  const send = useMutation({
    mutationFn: ({ meetingId, join, liveCopilot }: { meetingId: string; join: NotetakerJoin; liveCopilot: boolean }) =>
      join === "NOW"
        ? notetakerApi.scheduleNotetaker(projectId, candidateId, meetingId, liveCopilot, "NOW")
        : notetakerApi.scheduleNotetaker(projectId, candidateId, meetingId, liveCopilot),
    onSuccess: (sent) => {
      toast(sentLineOf(sent, firstName));
      void refresh();
    },
    onError,
  });
  const joinNow = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.joinNotetakerNow(projectId, candidateId, recordingId),
    onSuccess: () => {
      toast(`The Notetaker is joining the call with ${firstName} now. Let it in if it waits in the lobby.`);
      void refresh();
    },
    onError,
  });
  const end = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.endNotetaker(projectId, candidateId, recordingId),
    onSuccess: () => {
      toast(`The Notetaker is leaving the call. The notes land on ${firstName}'s Notes tab in a minute or two.`);
      void refresh();
    },
    onError,
  });
  const remove = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.cancelNotetaker(projectId, candidateId, recordingId),
    onSuccess: () => {
      toast("Notetaker removed. Nothing more is recorded.");
      void refresh();
    },
    onError,
  });
  const switchLive = useMutation({
    mutationFn: ({ recordingId, enabled }: { recordingId: string; enabled: boolean }) =>
      notetakerApi.switchLiveCopilot(projectId, candidateId, recordingId, enabled),
    onSuccess: () => void refresh(),
    onError,
  });
  const retry = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.retryNotetaker(projectId, candidateId, recordingId),
    onSuccess: () => void refresh(),
    onError: (error) => toast.error(messageFor(error)),
  });

  return { send, joinNow, end, remove, switchLive, retry };
}

function sentLineOf(sent: NotetakerRecording, firstName: string): string {
  if (sent.joinAt === null) {
    return sent.liveCopilot
      ? `The Notetaker is joining the call with ${firstName}, and the live copilot will follow it. Let it in if it waits in the lobby.`
      : `The Notetaker is joining the call with ${firstName}. Let it in if it waits in the lobby.`;
  }
  return sent.liveCopilot
    ? `The Notetaker will join the call with ${firstName} at its start, and the live copilot will follow it for you.`
    : `The Notetaker will join the call with ${firstName} at its start and file its notes afterwards.`;
}
