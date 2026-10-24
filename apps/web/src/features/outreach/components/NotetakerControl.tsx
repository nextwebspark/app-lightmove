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
 * and Suggest changes once its notes are filed, Try again when only the write-up failed. The bot says in the call's chat who it
 * records for.
 */
export function NotetakerControl({
  projectId,
  candidateId,
  meeting,
  recording,
  mayRecord,
  firstName,
  onShowTranscript,
  onSuggestChanges,
  onOpenCopilot,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  recording: NotetakerRecording | null;
  /** Offered here, the call is the caller's to record, and the person is not marked do not contact. */
  mayRecord: boolean;
  firstName: string;
  onShowTranscript: (recordingId: string) => void;
  /** Opens the review of what the call's transcript suggests for the person's record. */
  onSuggestChanges: (recordingId: string) => void;
  /** Opens the live copilot's window; called straight from the click, which the window needs. */
  onOpenCopilot: (recordingId: string) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isRemoving, setIsRemoving] = useState(false);
  const refresh = () => queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId) });

  const schedule = useMutation({
    mutationFn: (liveCopilot: boolean) =>
      notetakerApi.scheduleNotetaker(projectId, candidateId, meeting.id, liveCopilot),
    onSuccess: (sent) => {
      toast(
        sent.liveCopilot
          ? `The Notetaker will join the call with ${firstName}, and the live copilot will follow it for you.`
          : `The Notetaker will join the call with ${firstName} and file its notes afterwards.`,
      );
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
  const switchLive = useMutation({
    mutationFn: ({ recordingId, enabled }: { recordingId: string; enabled: boolean }) =>
      notetakerApi.switchLiveCopilot(projectId, candidateId, recordingId, enabled),
    onSuccess: () => void refresh(),
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
    if (next) schedule.mutate(false);
    else setIsRemoving(true);
  };
  // Recall streams a call only to a bot made for it: the copilot is chosen before the bot is in the call.
  const mayChooseCopilot = recording?.status === "SCHEDULED" || (!isOn && offerRecord);
  const isCopilotOn =
    (recording?.status === "SCHEDULED" && recording.liveCopilot) || (schedule.isPending && schedule.variables === true);
  const handleCopilotSwitch = (next: boolean) => {
    if (recording?.status === "SCHEDULED") switchLive.mutate({ recordingId: recording.id, enabled: next });
    else if (next) schedule.mutate(true);
  };
  const mayOpenCopilot =
    recording?.liveCopilot === true &&
    (recording.status === "JOINING" || recording.status === "RECORDING" || recording.status === "PROCESSING");

  return (
    <span className="block">
      <span
        className={cn("mt-1.5 flex items-center gap-2.5 rounded-u-control border px-2.5 py-[7px]", TONE_CLASS[tone])}
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
              <span
                aria-hidden="true"
                className="me-1.5 inline-block size-1.5 rounded-full bg-u-offlimits align-middle"
              />
            )}
            {line}
            {recording?.status === "SCHEDULED" && recording.liveCopilot && " · Live copilot on"}
          </span>
        </span>
        {mayOpenCopilot && recording && (
          <button type="button" onClick={() => onOpenCopilot(recording.id)} className={cn(ROW_ACTION, "text-u-accent")}>
            {recording.status === "PROCESSING" ? "Copilot ↗" : "Open copilot ↗"}
          </button>
        )}
        {chip?.action === "transcript" && recording && (
          <button type="button" onClick={() => onShowTranscript(recording.id)} className={ROW_ACTION}>
            View transcript
          </button>
        )}
        {recording?.status === "DONE" && recording.hasTranscript && isReviewed(recording) && (
          <span
            className={cn(
              "flex-none font-mono text-meta",
              recording.appliedSuggestions > 0 ? "text-u-direct" : "text-u-text3",
            )}
          >
            {reviewedLabelOf(recording)}
          </span>
        )}
        {recording?.status === "DONE" && recording.hasTranscript && !isReviewed(recording) && (
          <button
            type="button"
            onClick={() => onSuggestChanges(recording.id)}
            className={cn(ROW_ACTION, "inline-flex items-center gap-1 text-u-accent")}
          >
            <Icon d={ICONS.sparkle} size={12} />
            {suggestLabelOf(recording)}
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
            It leaves the call, or never joins it, and no notes are filed for this call. You can send it again while the
            call is still on.
          </p>
        </ConfirmDialog>
      </span>
      {mayChooseCopilot && (
        <span className="mt-1 flex items-center gap-2.5 rounded-u-control border border-u-border bg-u-bg px-2.5 py-1.5">
          <Icon
            d={ICONS.sparkle}
            size={13}
            className={cn("flex-none", isCopilotOn ? "text-u-accent" : "text-u-text3")}
          />
          <span className="min-w-0 flex-1">
            <span className="block text-note font-medium text-u-text">Live copilot</span>
            <span className="block font-mono text-meta text-u-text3">
              {isCopilotOn
                ? "Prompts you during the call · only you see it"
                : "Off · choose before the Notetaker joins"}
            </span>
          </span>
          <Toggle
            checked={isCopilotOn}
            onChange={handleCopilotSwitch}
            label="Live copilot"
            disabled={schedule.isPending || switchLive.isPending || remove.isPending}
          />
        </span>
      )}
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

/** Every change decided: the review is over, the row says what came of it, and the record takes it from there. */
function isReviewed(recording: NotetakerRecording): boolean {
  return recording.suggestionsAsked && recording.pendingSuggestions === 0;
}

function suggestLabelOf(recording: NotetakerRecording): string {
  if (!recording.suggestionsAsked) return "Suggest changes";
  return recording.pendingSuggestions === 1
    ? "1 change to review"
    : `${recording.pendingSuggestions} changes to review`;
}

function reviewedLabelOf(recording: NotetakerRecording): string {
  if (recording.appliedSuggestions === 0) return "No changes made";
  return recording.appliedSuggestions === 1 ? "1 change applied" : `${recording.appliedSuggestions} changes applied`;
}

const ROW_ACTION =
  "flex-none font-mono text-meta font-medium text-u-text3 underline underline-offset-2 hover:text-u-text disabled:opacity-60";
