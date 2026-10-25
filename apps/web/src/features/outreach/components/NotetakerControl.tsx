import { useMutation, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { Meeting } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import { elapsedSince } from "../lib/copilotWords";
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
          detail={
            <>
              Joins a minute early, records the call and files the notes on {firstName}&apos;s record.
            </>
          }
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
            mayToggleCopilot ? (
              <>
                Nudges you during the call with what is still to cover. Only you see it. Switching it on sends the
                Notetaker too.
              </>
            ) : (
              <>Only whoever sent the Notetaker can change the live copilot.</>
            )
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
            className={ROW_ACTION}
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
          <WithDetail
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
          </WithDetail>
        ) : (
          <WithDetail
            label="Live copilot"
            detail={<>It is chosen before the Notetaker joins, so it can&apos;t be added to this call.</>}
          >
            <span className="inline-flex flex-none cursor-default items-center gap-2 text-note text-u-text3">
              <Icon d={ICONS.sparkle} size={14} />
              Copilot off
            </span>
          </WithDetail>
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

  return (
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
        <span className={cn("block font-mono text-meta", LINE_CLASS[tone])}>{line}</span>
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

      {removeDialog}
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
    <WithDetail label={label} detail={detail}>
      <span className="inline-flex flex-none items-center gap-2">
        <Icon d={icon} size={14} className={cn("flex-none", checked ? "text-u-accent" : "text-u-text3")} />
        <span className="cursor-default text-note font-medium text-u-text">{label}</span>
        <Toggle checked={checked} onChange={onChange} label={switchLabel} disabled={disabled} />
      </span>
    </WithDetail>
  );
}

/** Opens an explanation on hover or focus, portalled so the drawer's scroll never clips it. */
function WithDetail({ label, detail, children }: { label: string; detail: ReactNode; children: ReactNode }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [anchor, setAnchor] = useState<DOMRect | null>(null);
  const tipId = useId();
  const show = () => ref.current && setAnchor(ref.current.getBoundingClientRect());
  const hide = () => setAnchor(null);
  return (
    <span
      ref={ref}
      onMouseEnter={show}
      onMouseLeave={hide}
      onFocus={show}
      onBlur={hide}
      aria-describedby={anchor ? tipId : undefined}
      className="inline-flex flex-none"
    >
      {children}
      {anchor &&
        createPortal(
          <span
            id={tipId}
            role="tooltip"
            style={{
              top: anchor.bottom + 8,
              left: Math.max(12, Math.min(anchor.left - 4, window.innerWidth - DETAIL_WIDTH - 12)),
              width: DETAIL_WIDTH,
            }}
            className="pointer-events-none fixed z-[200] rounded-u-chip border border-u-border-strong bg-u-surface px-3 py-2.5 text-note leading-relaxed text-u-text2 shadow-u-e3"
          >
            <span className="mb-0.5 block font-medium text-u-text">{label}</span>
            {detail}
          </span>,
          document.body,
        )}
    </span>
  );
}

const DETAIL_WIDTH = 300;

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
