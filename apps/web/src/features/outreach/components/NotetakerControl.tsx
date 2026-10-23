import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { Meeting } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import { canRecord, notetakerChipOf, type NotetakerTone } from "../lib/notetakerChip";

const TONE_CLASS: Record<NotetakerTone, string> = {
  waiting: "bg-u-raised text-u-text2 border-u-border",
  live: "bg-u-accent-tint text-u-accent border-u-accent",
  done: "bg-u-direct-tint text-u-direct border-transparent",
  failed: "bg-u-signal-tint text-u-text2 border-u-signal/40",
};

/**
 * One meeting row's Notetaker: Record with Notetaker on a call still to come that has a video link, then the bot's
 * chip and whatever may be done from it — Remove before or during the call, the transcript once its notes are filed,
 * Try again when only the write-up failed. The bot says in the call's chat who it records for.
 */
export function NotetakerControl({
  projectId,
  candidateId,
  meeting,
  recording,
  upcoming,
  mayRecord,
  firstName,
  onShowTranscript,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  recording: NotetakerRecording | null;
  upcoming: boolean;
  /** Offered here, the call is the caller's to record, and the person is not marked do not contact. */
  mayRecord: boolean;
  firstName: string;
  onShowTranscript: (recordingId: string) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isRemoving, setIsRemoving] = useState(false);
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId) });

  const schedule = useMutation({
    mutationFn: () => notetakerApi.scheduleNotetaker(projectId, candidateId, meeting.id),
    onSuccess: () => {
      toast(`The Notetaker will join the call with ${firstName} and file its notes afterwards.`);
      void refresh();
    },
    onError: (error) => {
      toast.error(messageFor(error));
      void refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.cancelNotetaker(projectId, candidateId, recordingId),
    onSuccess: () => {
      toast("Notetaker removed. Nothing more is recorded.");
      setIsRemoving(false);
      void refresh();
    },
    onError: (error) => {
      toast.error(messageFor(error));
      void refresh();
    },
  });
  const retry = useMutation({
    mutationFn: (recordingId: string) => notetakerApi.retryNotetaker(projectId, candidateId, recordingId),
    onSuccess: () => void refresh(),
    onError: (error) => toast.error(messageFor(error)),
  });

  const chip = recording ? notetakerChipOf(recording) : null;
  const offerRecord = upcoming && meeting.joinUrl !== null && mayRecord && canRecord(recording);
  if (!chip && !offerRecord) return null;

  return (
    <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
      {chip && (
        <span
          className={cn(
            "inline-flex rounded-u-control border px-[7px] py-px font-mono text-eyebrow font-semibold",
            TONE_CLASS[chip.tone],
          )}
        >
          {chip.label}
        </span>
      )}
      {chip?.action === "remove" && recording && (
        <button type="button" onClick={() => setIsRemoving(true)} className={ROW_ACTION}>
          Remove notetaker
        </button>
      )}
      {chip?.action === "transcript" && recording && (
        <button type="button" onClick={() => onShowTranscript(recording.id)} className={ROW_ACTION}>
          View transcript
        </button>
      )}
      {chip?.action === "retry" && recording && (
        <button
          type="button"
          disabled={retry.isPending}
          onClick={() => retry.mutate(recording.id)}
          className={ROW_ACTION}
        >
          Try again
        </button>
      )}
      {offerRecord && (
        <button type="button" disabled={schedule.isPending} onClick={() => schedule.mutate()} className={ROW_ACTION}>
          {schedule.isPending ? "Sending…" : "Record with Notetaker"}
        </button>
      )}

      <ConfirmDialog
        open={isRemoving}
        title="Remove the Notetaker?"
        confirmLabel="Remove notetaker"
        cancelLabel="Keep it"
        pending={remove.isPending}
        onConfirm={() => recording && remove.mutate(recording.id)}
        onClose={() => setIsRemoving(false)}
      >
        <p>
          It leaves the call, or never joins it, and no notes are filed for this call. You can send it again before
          the call ends.
        </p>
      </ConfirmDialog>
    </span>
  );
}

const ROW_ACTION =
  "font-mono text-meta font-medium text-u-text3 underline-offset-2 hover:text-u-text hover:underline disabled:opacity-60";
