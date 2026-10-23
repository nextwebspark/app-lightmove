import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";

/** How the Meetings section draws one Notetaker bot: the chip's words, its colour and what may be done from it. */
export type NotetakerTone = "waiting" | "live" | "done" | "failed";

export type NotetakerAction = "remove" | "transcript" | "retry" | null;

export interface NotetakerChip {
  label: string;
  tone: NotetakerTone;
  action: NotetakerAction;
}

const FAILURE_WORDS: Record<string, string> = {
  SEND_FAILED: "it couldn't be sent",
  NEVER_RECORDED: "it never got into the call",
  TRANSCRIPT_FAILED: "the call couldn't be transcribed",
  TRANSCRIPT_UNAVAILABLE: "the transcript couldn't be read",
  NOTHING_HEARD: "nothing was heard on the call",
  SUMMARY_FAILED: "the notes couldn't be written",
  TIMED_OUT: "it was never heard from again",
  FATAL: "it stopped unexpectedly",
  RECORDING_PERMISSION_DENIED: "the host didn't allow recording",
  timeout_exceeded_waiting_room: "nobody let it in",
  timeout_exceeded_noone_joined: "nobody joined the call",
  timeout_exceeded_everyone_left: "everyone had left",
  meeting_not_found: "the meeting link didn't work",
  meeting_not_started: "the meeting hadn't started",
  meeting_password_incorrect: "the meeting needs a passcode",
  bot_kicked_from_waiting_room: "it was turned away from the lobby",
  bot_kicked_from_call: "it was removed from the call",
  recording_permission_denied: "the host didn't allow recording",
};

/** The reason a run failed, in words; one we have not met yet still says something true. */
export function failureWordsOf(reason: string | null): string {
  return (reason && FAILURE_WORDS[reason]) ?? "something went wrong";
}

export function notetakerChipOf(recording: NotetakerRecording): NotetakerChip | null {
  switch (recording.status) {
    case "SCHEDULED":
      return { label: "Notetaker scheduled", tone: "waiting", action: "remove" };
    case "JOINING":
      return { label: "Notetaker joining", tone: "live", action: "remove" };
    case "RECORDING":
      return { label: "Recording", tone: "live", action: "remove" };
    case "PROCESSING":
      return { label: "Writing notes", tone: "waiting", action: null };
    case "DONE":
      return { label: "Notes filed", tone: "done", action: recording.hasTranscript ? "transcript" : null };
    case "FAILED":
      return {
        label: `Notetaker failed: ${failureWordsOf(recording.failureReason)}`,
        tone: "failed",
        action: recording.retryable ? "retry" : null,
      };
    case "CANCELLED":
      return null;
  }
}

/**
 * The latest bot sent to this call: by its meeting row, or by its start and title where a calendar's re-read gave the
 * call a new row — the same two the server tells calls apart by. Newest call first, latest attempt first within one.
 */
export function recordingOfMeeting(recordings: NotetakerRecording[], meeting: Meeting): NotetakerRecording | null {
  return (
    recordings.find(
      (recording) =>
        recording.meetingId === meeting.id ||
        (new Date(recording.startsAt).getTime() === new Date(meeting.startsAt).getTime() &&
          titleKeyOf(recording.title) === titleKeyOf(meeting.title)),
    ) ?? null
  );
}

function titleKeyOf(title: string | null): string {
  return (title ?? "").trim().toLowerCase();
}

/** Whether any bot is still on its way, in a call or being written up: the section reads again while one is. */
export function hasLiveRecording(recordings: NotetakerRecording[]): boolean {
  return recordings.some((recording) =>
    ["SCHEDULED", "JOINING", "RECORDING", "PROCESSING"].includes(recording.status),
  );
}

/** A call may be sent a bot when none is on its way to it already. */
export function canRecord(recording: NotetakerRecording | null): boolean {
  return recording === null || recording.status === "CANCELLED" || recording.status === "FAILED";
}
