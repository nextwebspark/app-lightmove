import { useMutation, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { HoverDetail, Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { Meeting } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import { elapsedSince, LIVE_COPILOT_DETAIL, notetakerDetailOf } from "../lib/copilotWords";
import {
  canRecord,
  failureWordsOf,
  notetakerChipOf,
} from "../lib/notetakerChip";

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
  const handleSwitch = (next: boolean) => {
    if (next) schedule.mutate(false);
    else setIsRemoving(true);
  };
  // Recall streams a call only to a bot made for it: the copilot is chosen before the bot is in the call.
  // Only the bot's sender switches its copilot: it reconfigures their bot. Anyone may choose it before sending one.
  const mayChooseCopilot =
    (recording?.status === "SCHEDULED" && recording.mayChangeCopilot) || (!isOn && offerRecord);
  const isCopilotOn =
    (recording?.status === "SCHEDULED" && recording.liveCopilot) || (schedule.isPending && schedule.variables === true);
  const handleCopilotSwitch = (next: boolean) => {
    if (recording?.status === "SCHEDULED") switchLive.mutate({ recordingId: recording.id, enabled: next });
    else if (next) schedule.mutate(true);
  };
  const mayOpenCopilot =
    recording?.liveCopilot === true &&
    (recording.status === "JOINING" || recording.status === "RECORDING" || recording.status === "PROCESSING");

  const removeDialog = (
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
        It leaves the call, or never joins it, and no notes are filed for this call. You can send it again while the call
        is still on.
      </p>
    </ConfirmDialog>
  );

  // Before the bot is in the call both choices are live, so they share one line and explain themselves on hover.
  if (hasSwitch && recording?.status !== "JOINING" && recording?.status !== "RECORDING") {
    const isNotetakerOn = isOn || schedule.isPending;
    const mayToggleCopilot = mayChooseCopilot || (recording?.status === "SCHEDULED" && recording.mayChangeCopilot);
    const status = schedule.isPending ? "Sending…" : shortLineOf(recording, timing);
    return (
      <span className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-u-control border border-u-border bg-u-bg px-2.5 py-[7px]">
        <SwitchWithDetail
          icon={ICONS.mic}
          label="Notetaker"
          switchLabel="Record with Notetaker"
          checked={isNotetakerOn}
          onChange={handleSwitch}
          disabled={schedule.isPending || remove.isPending}
          detail={notetakerDetailOf(firstName)}
        />
        <span aria-hidden="true" className="h-4 w-px flex-none bg-u-border" />
        <SwitchWithDetail
          icon={ICONS.sparkle}
          label="Live copilot"
          switchLabel="Live copilot"
          checked={isCopilotOn}
          onChange={handleCopilotSwitch}
          disabled={!mayToggleCopilot || schedule.isPending || switchLive.isPending || remove.isPending}
          detail={
            mayToggleCopilot ? LIVE_COPILOT_DETAIL : "Only whoever sent the Notetaker can change the live copilot."
          }
        />
        <span
          title={status}
          className={cn(
            "ms-auto min-w-0 truncate font-mono text-meta",
            isNotetakerOn ? "text-u-accent" : recording?.status === "FAILED" ? "text-u-text2" : "text-u-text3",
          )}
        >
          {status}
        </span>
        {chip?.action === "retry" && recording && (
          <button
            type="button"
            disabled={retry.isPending}
            onClick={() => retry.mutate(recording.id)}
            className={SECONDARY_ACTION}
          >
            Try again
          </button>
        )}
        {removeDialog}
      </span>
    );
  }

  // In the call the strip keeps its shape: the copilot's half becomes the way into it, the end says how long it has run.
  if (hasSwitch && recording && (recording.status === "JOINING" || recording.status === "RECORDING")) {
    const isRecording = recording.status === "RECORDING";
    return (
      <span className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-u-control border border-u-accent/30 bg-u-surface px-2.5 py-[5px]">
        <SwitchWithDetail
          icon={ICONS.mic}
          label="Notetaker"
          switchLabel="Record with Notetaker"
          checked
          onChange={handleSwitch}
          disabled={remove.isPending}
          detail={
            isRecording ? (
              <>Recording the call. Switch it off to take the Notetaker out; nothing more is recorded.</>
            ) : (
              <>On its way into the call. If it waits in the lobby, let it in from the call.</>
            )
          }
        />
        <span aria-hidden="true" className="h-4 w-px flex-none bg-u-border" />
        {recording.liveCopilot ? (
          <HoverDetail
            label="Live copilot"
            detail={<>Coverage, what to ask next and the transcript, in a window that floats over the call.</>}
          >
            <button
              type="button"
              onClick={() => onOpenCopilot(recording.id)}
              className="inline-flex flex-none items-center gap-1.5 rounded-u-control bg-u-accent-solid px-2.5 py-1 text-note font-semibold text-white hover:bg-u-accent-solid-hover"
            >
              <Icon d={ICONS.sparkle} size={13} />
              Open copilot
              <Icon d={ICONS.externalLink} size={12} className="opacity-80" />
            </button>
          </HoverDetail>
        ) : (
          <HoverDetail
            label="Live copilot"
            detail={<>It is chosen before the Notetaker joins, so it can&apos;t be added to this call.</>}
          >
            <span className="inline-flex flex-none cursor-default items-center gap-2 text-note text-u-text3">
              <Icon d={ICONS.sparkle} size={14} />
              Copilot off
            </span>
          </HoverDetail>
        )}
        <span className="ms-auto flex flex-none items-center gap-1.5 font-mono text-meta">
          <span
            aria-hidden="true"
            className={cn(
              "size-1.5 rounded-full",
              isRecording ? "animate-pulse bg-u-offlimits" : "animate-pulse bg-u-text3",
            )}
          />
          {isRecording ? (
            <>
              <span className="text-u-text2">Rec</span>
              <ElapsedClock since={recording.startsAt} />
            </>
          ) : (
            <span className="text-u-text3">Joining…</span>
          )}
        </span>
        {removeDialog}
      </span>
    );
  }

  // After the call the same strip says what came of it, and offers the one next step as its solid button.
  if (!recording) return null;
  return (
    <span className="mt-1.5 flex items-center gap-3 rounded-u-control border border-u-border bg-u-bg px-2.5 py-[5px]">
      <AfterCallStatus recording={recording} firstName={firstName} />
      <span className="ms-auto flex flex-none items-center gap-2">
        {mayOpenCopilot && (
          <button type="button" onClick={() => onOpenCopilot(recording.id)} className={SECONDARY_ACTION}>
            <Icon d={ICONS.sparkle} size={12} />
            Open copilot
          </button>
        )}
        {chip?.action === "transcript" && (
          <button type="button" onClick={() => onShowTranscript(recording.id)} className={SECONDARY_ACTION}>
            <Icon d={ICONS.fileText} size={12} />
            Transcript
          </button>
        )}
        {recording.status === "DONE" && recording.hasTranscript && isReviewed(recording) && (
          <span
            className={cn(
              "inline-flex items-center gap-1 font-mono text-meta",
              recording.appliedSuggestions > 0 ? "text-u-direct" : "text-u-text3",
            )}
          >
            {recording.appliedSuggestions > 0 && <Icon d={ICONS.check} size={12} />}
            {reviewedLabelOf(recording)}
          </span>
        )}
        {recording.status === "DONE" && recording.hasTranscript && !isReviewed(recording) && (
          <HoverDetail
            label={suggestLabelOf(recording)}
            detail={
              recording.suggestionsAsked ? (
                <>Changes from the call, waiting for you to keep or skip.</>
              ) : (
                <>Finds what the call changes on {firstName}&apos;s record. You keep or skip each one.</>
              )
            }
          >
            <button type="button" onClick={() => onSuggestChanges(recording.id)} className={PRIMARY_ACTION}>
              <Icon d={ICONS.sparkle} size={13} />
              {suggestLabelOf(recording)}
            </button>
          </HoverDetail>
        )}
        {chip?.action === "retry" && (
          <button
            type="button"
            disabled={retry.isPending}
            onClick={() => retry.mutate(recording.id)}
            className={SECONDARY_ACTION}
          >
            Try again
          </button>
        )}
      </span>
    </span>
  );
}

/** The after-call strip's lead: what came of the Notetaker, with what it means on hover. */
function AfterCallStatus({ recording, firstName }: { recording: NotetakerRecording; firstName: string }) {
  if (recording.status === "PROCESSING") {
    return (
      <HoverDetail
        label="Writing notes"
        detail={<>The call is over. The Notetaker is writing it up; the notes land on {firstName}&apos;s Notes tab.</>}
      >
        <span className="inline-flex flex-none items-center gap-2 text-note font-medium text-u-text">
          <span
            aria-hidden="true"
            className="size-3.5 flex-none animate-spin rounded-full border-2 border-u-accent-ring border-t-u-accent"
          />
          Notetaker is writing notes…
        </span>
      </HoverDetail>
    );
  }
  if (recording.status === "DONE") {
    return (
      <HoverDetail
        label="Notes filed"
        detail={<>A Meeting note on {firstName}&apos;s Notes tab: what was said, read against the brief.</>}
      >
        <span className="inline-flex flex-none items-center gap-2 text-note font-medium text-u-direct">
          <Icon d={ICONS.checkCircle} size={14} className="flex-none" />
          Notes filed
        </span>
      </HoverDetail>
    );
  }
  const reason = failureWordsOf(recording.failureReason);
  return (
    <HoverDetail
      className="min-w-0"
      label="Notetaker failed"
      detail={
        recording.retryable ? (
          <>The call was recorded but {reason}. Try again writes them from the same recording.</>
        ) : (
          <>No notes were filed for this call because {reason}.</>
        )
      }
    >
      <span className="inline-flex min-w-0 items-center gap-2 text-note">
        <Icon d={ICONS.warning} size={14} className="flex-none text-u-signal" />
        <span className="flex-none font-medium text-u-text">Notetaker failed</span>
        <span className="min-w-0 truncate font-mono text-meta text-u-text2">{reason}</span>
      </span>
    </HoverDetail>
  );
}

type CallTiming = "ahead" | "on" | "ended";

const SHORT_OFF_LINE: Record<CallTiming, string> = {
  ahead: "Not recorded",
  on: "Call is on · not recorded",
  ended: "Past its end · still on?",
};

/** The one-line strip's status in a few words; the hover says the rest. */
function shortLineOf(recording: NotetakerRecording | null, timing: CallTiming): string {
  if (recording?.status === "SCHEDULED") return timing === "ahead" ? "Joins 1 min early" : "Joining now";
  if (recording?.status === "FAILED") return `Failed: ${failureWordsOf(recording.failureReason)}`;
  return SHORT_OFF_LINE[timing];
}

/** How long the call has run, by its booked start, ticking each second as the copilot's window does. */
function ElapsedClock({ since }: { since: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="tabular-nums text-u-text2">{elapsedSince(since, now)}</span>;
}

/** A labelled switch whose explanation opens on hover or focus. */
function SwitchWithDetail({
  icon,
  label,
  switchLabel,
  checked,
  onChange,
  disabled,
  detail,
}: {
  icon: string;
  label: string;
  switchLabel: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  disabled: boolean;
  detail: ReactNode;
}) {
  return (
    <HoverDetail label={label} detail={detail}>
      <span className="inline-flex flex-none items-center gap-2">
        <Icon d={icon} size={14} className={cn("flex-none", checked ? "text-u-accent" : "text-u-text3")} />
        <span className="cursor-default text-note font-medium text-u-text">{label}</span>
        <Toggle checked={checked} onChange={onChange} label={switchLabel} disabled={disabled} />
      </span>
    </HoverDetail>
  );
}

/** Every change decided: the review is over, the row says what came of it, and the record takes it from there. */
function isReviewed(recording: NotetakerRecording): boolean {
  return recording.suggestionsAsked && recording.pendingSuggestions === 0;
}

function suggestLabelOf(recording: NotetakerRecording): string {
  if (!recording.suggestionsAsked) return "Suggest changes";
  return recording.pendingSuggestions === 1 ? "Review 1 change" : `Review ${recording.pendingSuggestions} changes`;
}

function reviewedLabelOf(recording: NotetakerRecording): string {
  if (recording.appliedSuggestions === 0) return "No changes made";
  return recording.appliedSuggestions === 1 ? "1 change applied" : `${recording.appliedSuggestions} changes applied`;
}

const SECONDARY_ACTION =
  "inline-flex flex-none items-center gap-1.5 rounded-u-control border border-u-border bg-u-surface px-2.5 py-1 text-note font-medium text-u-text2 hover:border-u-border-strong hover:text-u-text disabled:opacity-60";

const PRIMARY_ACTION =
  "inline-flex flex-none items-center gap-1.5 rounded-u-control bg-u-accent-solid px-2.5 py-1 text-note font-semibold text-white hover:bg-u-accent-solid-hover";
