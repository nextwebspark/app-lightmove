import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";

const FAILURE_WORDS: Record<string, string> = {
  SEND_FAILED: "it couldn't be sent",
  RECALL_BUSY: "no Notetaker was free to join",
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
  timeout_exceeded_in_call_not_recording: "it was never allowed to record",
  timeout_exceeded_silence_detected: "the call was silent",
  timeout_exceeded_only_bots_in_call: "only other bots were left in the call",
  timeout_exceeded_recording_permission_denied: "the host didn't allow recording",
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

/**
 * A bot about to move — sent at once, waiting in the lobby, asked to leave, its notes being written or its changes
 * being found — is read again sooner, since the consultant is watching the call for the next step.
 */
export function hasRecordingAboutToMove(recordings: NotetakerRecording[]): boolean {
  return recordings.some(
    (recording) =>
      (recording.status === "SCHEDULED" && recording.joinAt === null) ||
      recording.status === "JOINING" ||
      (recording.status === "RECORDING" && recording.endRequested) ||
      recording.status === "PROCESSING" ||
      (recording.status === "DONE" && recording.suggesting),
  );
}

/** The call is over for its Notetaker: ended from the row, or its notes being written or filed. */
export function hasCallEnded(recording: NotetakerRecording | null): boolean {
  return (
    recording !== null &&
    ((recording.status === "RECORDING" && recording.endRequested) ||
      recording.status === "PROCESSING" ||
      recording.status === "DONE")
  );
}

/** The bot is set up for the call or in it: what the meeting's panel offers to change. */
export function isNotetakerLive(recording: NotetakerRecording | null): boolean {
  return (
    recording !== null &&
    (recording.status === "SCHEDULED" || recording.status === "JOINING" || recording.status === "RECORDING")
  );
}

/** A call may be sent a bot when none is on its way to it already. */
export function canRecord(recording: NotetakerRecording | null): boolean {
  return recording === null || recording.status === "CANCELLED" || recording.status === "FAILED";
}
