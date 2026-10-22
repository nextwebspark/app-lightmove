import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import type { Meeting } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { NotetakerMeeting, NotetakerMeetings } from "../api/notetakerApi";
import { clockTimeOf } from "../lib/meetingTimes";
import { notetakerChipOf, type NotetakerChip, type NotetakerChipTone } from "../lib/notetakerChip";
import { TranscriptSheet } from "./TranscriptSheet";

const TONES: Record<NotetakerChipTone, string> = {
  neutral: "bg-u-raised text-u-text2",
  waiting: "bg-u-signal-tint text-u-signal",
  live: "bg-u-offlimits-tint text-u-offlimits",
  working: "bg-u-accent-tint text-u-accent",
  done: "bg-u-direct-tint text-u-direct",
  partial: "bg-u-signal-tint text-u-signal",
  problem: "bg-u-offlimits-tint text-u-offlimits",
};

const LINK = "text-meta font-medium text-u-accent hover:underline";

/** Says which standing switch keeps the bot out of every call, while there is a call it would otherwise join. */
export function NotetakerOffNotice({ notetaker, hasJoinableCall }: { notetaker: NotetakerMeetings; hasJoinableCall: boolean }) {
  if (!hasJoinableCall || (notetaker.workspaceEnabled && notetaker.myMeetingsEnabled)) return null;
  return (
    <div className="mb-2.5 flex items-center gap-2 rounded-u-chip border border-u-border bg-u-raised px-2.5 py-[7px] text-note text-u-text2">
      {notetaker.workspaceEnabled ? (
        <>
          <span className="flex-1">You've turned the notetaker off for your calls.</span>
          <Link to="/settings/profile" className="text-note font-semibold text-u-accent">
            Profile
          </Link>
        </>
      ) : (
        <span className="flex-1">
          Your workspace has the notetaker turned off. An admin turns it on in Settings → Integrations.
        </span>
      )}
    </div>
  );
}

/** Under an upcoming call: its switch before a bot is sent, its chip after, or why it never joins this one. */
export function UpcomingNotetaker({
  projectId,
  candidateId,
  meeting,
  notetaker,
  standing,
  joinsLine,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  notetaker: NotetakerMeeting | null;
  standing: NotetakerMeetings;
  joinsLine: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const toggle = useMutation({
    mutationFn: (enabled: boolean) => notetakerApi.setMeetingNotetaker(projectId, candidateId, meeting.id, enabled),
    onSuccess: (_, enabled) => {
      toast(enabled ? "The notetaker will join this call" : "The notetaker won't join this call");
      void queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId) });
    },
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId) });
    },
  });

  if (!notetaker || !meeting.joinUrl) return null;
  if (notetaker.status) {
    return <NotetakerChipRow projectId={projectId} candidateId={candidateId} meeting={meeting} notetaker={notetaker} />;
  }
  if (!notetaker.joinable) {
    return <span className="mt-1.5 block font-mono text-meta text-u-text3">{joinsLine}</span>;
  }
  if (new Date(meeting.startsAt).getTime() <= Date.now()) return null;
  const standingOff = !standing.workspaceEnabled || !standing.myMeetingsEnabled;
  const on = notetaker.enabled && !standingOff;
  return (
    <span className="mt-1.5 flex items-center gap-[7px]">
      <Toggle
        checked={on}
        label="Uncava Notetaker joins this call"
        disabled={standingOff || toggle.isPending}
        onChange={(enabled) => toggle.mutate(enabled)}
      />
      <span className="text-note text-u-text2">{on ? "Notetaker joins" : "Notetaker off"}</span>
    </span>
  );
}

/** On a past call: what came of its bot — notes ready, partial, failed with Try again, or filed elsewhere. */
export function PastNotetaker({
  projectId,
  candidateId,
  meeting,
  notetaker,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  notetaker: NotetakerMeeting | null;
}) {
  if (!notetaker?.status) return null;
  return (
    <NotetakerChipRow
      projectId={projectId}
      candidateId={candidateId}
      meeting={meeting}
      notetaker={notetaker}
      className="mt-0 flex-none"
    />
  );
}

function NotetakerChipRow({
  projectId,
  candidateId,
  meeting,
  notetaker,
  className,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  notetaker: NotetakerMeeting;
  className?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirmingRemove, setConfirmingRemove] = useState(false);
  const [reading, setReading] = useState(false);
  const refresh = () =>
    void queryClient.invalidateQueries({ queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId) });
  const remove = useMutation({
    mutationFn: () => notetakerApi.removeNotetaker(projectId, candidateId, meeting.id),
    onSuccess: () => {
      toast("The notetaker is leaving the call. Nothing it recorded is kept.");
      setConfirmingRemove(false);
      refresh();
    },
    onError: (error) => {
      toast.error(messageFor(error));
      if (codeOf(error) === "NOTETAKER_NOT_IN_CALL") setConfirmingRemove(false);
      refresh();
    },
  });
  const retry = useMutation({
    mutationFn: () => notetakerApi.retryNotes(projectId, candidateId, meeting.id),
    onSuccess: () => {
      toast("Trying again. The note follows in a few minutes.");
      refresh();
    },
    onError: (error) => {
      toast.error(messageFor(error));
      refresh();
    },
  });

  const chip = notetakerChipOf(notetaker, clockTimeOf);
  if (!chip) return null;
  return (
    <span className={cn("mt-1.5 flex flex-wrap items-center gap-2", className)}>
      <NotetakerChipLabel chip={chip} />
      {chip.removable && (
        <button
          type="button"
          onClick={() => setConfirmingRemove(true)}
          className="text-meta font-medium text-u-text3 underline hover:text-u-offlimits"
        >
          Remove notetaker
        </button>
      )}
      {chip.readable && (
        <button type="button" onClick={() => setReading(true)} className={LINK}>
          View transcript
        </button>
      )}
      {chip.elsewhere && <span className="font-mono text-meta text-u-text3">On another position</span>}
      {chip.retryable && (
        <button
          type="button"
          onClick={() => retry.mutate()}
          disabled={retry.isPending}
          className="rounded-u-control border border-u-border bg-u-surface px-[9px] py-0.5 text-meta font-semibold text-u-text hover:bg-u-raised disabled:opacity-60"
        >
          Try again
        </button>
      )}
      <ConfirmDialog
        open={confirmingRemove}
        title="Remove the notetaker?"
        confirmLabel="Remove notetaker"
        cancelLabel="Keep recording"
        tone="danger"
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirmingRemove(false)}
      >
        <p>The notetaker leaves the call; nothing recorded is kept.</p>
      </ConfirmDialog>
      {reading && notetaker.recordingId && (
        <TranscriptSheet
          projectId={projectId}
          candidateId={candidateId}
          recordingId={notetaker.recordingId}
          onClose={() => setReading(false)}
        />
      )}
    </span>
  );
}

function NotetakerChipLabel({ chip }: { chip: NotetakerChip }) {
  const live = chip.state === "recording" || chip.state === "backup";
  return (
    <span
      title={chip.title}
      className={cn(
        "inline-flex items-center gap-[5px] rounded-u-control px-[7px] py-px font-mono text-eyebrow font-semibold",
        TONES[chip.tone],
      )}
    >
      {live && <span aria-hidden className="size-1.5 animate-pulse rounded-full bg-current" />}
      {chip.label}
    </span>
  );
}

/**
 * A Meeting note's foot when the Notetaker filed it: who recorded it, whether it is partial, and its transcript —
 * found by the note, so a call whose meeting row a calendar read deleted still opens. Nothing for a typed note.
 */
export function MeetingNoteRecording({
  projectId,
  candidateId,
  noteId,
}: {
  projectId: string;
  candidateId: string;
  noteId: string;
}) {
  const [reading, setReading] = useState(false);
  const notetaker = useQuery({
    queryKey: notetakerApi.NOTETAKER_MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => notetakerApi.getNotetakerMeetings(projectId, candidateId, signal),
  });
  const recorded = notetaker.data?.recordings.find((recording) => recording.noteId === noteId);
  if (!recorded) return null;
  return (
    <div className="mt-2.5 flex flex-wrap items-center gap-2.5 text-meta text-u-text3">
      <span>Recorded by Uncava Notetaker</span>
      {recorded.recordedUntil && (
        <span
          title="The notetaker stopped before the call ended; the note covers what it heard."
          className="rounded-u-control bg-u-signal-tint px-[7px] py-px font-mono text-eyebrow font-semibold text-u-signal"
        >
          Partial · stopped at {clockTimeOf(recorded.recordedUntil)}
        </span>
      )}
      <button type="button" onClick={() => setReading(true)} className={LINK}>
        View transcript
      </button>
      {reading && (
        <TranscriptSheet
          projectId={projectId}
          candidateId={candidateId}
          recordingId={recorded.recordingId}
          onClose={() => setReading(false)}
        />
      )}
    </div>
  );
}
