import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { Meeting } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import {
  canRecord,
  failureWordsOf,
  type NotetakerChip,
  notetakerChipOf,
  type NotetakerTone,
} from "../lib/notetakerChip";

const TONE_CLASS: Record<NotetakerTone, string> = {
  waiting: "border-u-border bg-u-bg",
  live: "border-u-border bg-u-bg",
  done: "border-transparent bg-u-direct-tint",
  failed: "border-u-signal/40 bg-u-signal-tint",
};

const LINE_CLASS: Record<NotetakerTone, string> = {
  waiting: "text-u-text3",
  live: "text-u-accent",
  done: "text-u-direct",
  failed: "text-u-text2",
};

/**
 * One meeting row's Notetaker, a strip with a switch while the server still offers the call — before it, during it,
 * or while it runs over: on sends the bot, off asks and then removes it. Once the call is over the strip says what came of it — the transcript
 * once its notes are filed, Try again when only the write-up failed. The bot says in the call's chat who it records for.
 */
export function NotetakerControl({
  projectId,
  candidateId,
  meeting,
  recording,
  mayRecord,
  firstName,
  onShowTranscript,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  recording: NotetakerRecording | null;
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
  const offerRecord = meeting.joinUrl !== null && mayRecord && canRecord(recording);
  const now = Date.now();
  const timing: CallTiming =
    new Date(meeting.endsAt).getTime() <= now ? "ended" : new Date(meeting.startsAt).getTime() <= now ? "on" : "ahead";
  if (!chip && !offerRecord) return null;

  const isOn = chip?.action === "remove";
  const hasSwitch = isOn || offerRecord;
  const tone: NotetakerTone = isOn || !chip ? "waiting" : chip.tone;
  const line = schedule.isPending ? "Sending…" : lineOf(recording, chip, timing);
  const handleSwitch = (next: boolean) => {
    if (next) schedule.mutate();
    else setIsRemoving(true);
  };

  return (
    <span
      className={cn(
        "mt-1.5 flex items-center gap-2.5 rounded-u-control border px-2.5 py-[7px]",
        TONE_CLASS[tone],
      )}
    >
      <Icon
        d={ICONS.mic}
        size={14}
        className={cn("flex-none", isOn ? "text-u-accent" : tone === "done" ? "text-u-direct" : "text-u-text3")}
      />
      <span className="min-w-0 flex-1">
        <span className="block text-note font-medium text-u-text">Notetaker</span>
        <span
          className={cn(
            "block font-mono text-meta",
            recording?.status === "RECORDING" ? "text-u-accent" : LINE_CLASS[tone],
          )}
        >
          {recording?.status === "RECORDING" && (
            <span aria-hidden="true" className="me-1.5 inline-block size-1.5 rounded-full bg-u-offlimits align-middle" />
          )}
          {line}
        </span>
      </span>
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
      {hasSwitch && (
        <Toggle
          checked={isOn || schedule.isPending}
          onChange={handleSwitch}
          label="Record with Notetaker"
          disabled={schedule.isPending || remove.isPending}
        />
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
          It leaves the call, or never joins it, and no notes are filed for this call. You can send it again while
          the call is still on.
        </p>
      </ConfirmDialog>
    </span>
  );
}

/** What the strip's second line says: the switch's state, or what came of the bot. */
type CallTiming = "ahead" | "on" | "ended";

const OFF_LINE: Record<CallTiming, string> = {
  ahead: "Off · nothing is recorded",
  on: "Off · add it now to record the rest",
  ended: "Booked end passed · still on? Add it now",
};

function lineOf(recording: NotetakerRecording | null, chip: NotetakerChip | null, timing: CallTiming): string {
  const off = OFF_LINE[timing];
  if (!recording || !chip) return off;
  switch (recording.status) {
    case "SCHEDULED":
      return timing === "ahead" ? "Joins 1 min early · files notes after" : "Joining now · files notes after";
    case "JOINING":
      return "Joining the call";
    case "RECORDING":
      return "Recording";
    case "PROCESSING":
      return "Writing notes";
    case "DONE":
      return "Notes filed";
    case "FAILED":
      return `Failed: ${failureWordsOf(recording.failureReason)}`;
    case "CANCELLED":
      return off;
  }
}

const ROW_ACTION =
  "flex-none font-mono text-meta font-medium text-u-text3 underline underline-offset-2 hover:text-u-text disabled:opacity-60";
