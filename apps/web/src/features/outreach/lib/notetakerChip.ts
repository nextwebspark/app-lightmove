import { countOf } from "../../../lib/format";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";

const FAILURE_WORDS: Record<string, string> = {
  SEND_FAILED: "the Notetaker couldn't be sent to the call",
  RECALL_BUSY: "no Notetaker was free to join",
  NEVER_RECORDED: "the Notetaker never got into the call",
  TRANSCRIPT_FAILED: "the call couldn't be transcribed",
  TRANSCRIPT_UNAVAILABLE: "the transcript couldn't be read",
  NOTHING_HEARD: "no speech was picked up on the call",
  SUMMARY_FAILED: "the notes couldn't be written",
  TIMED_OUT: "the Notetaker lost contact with the call",
  FATAL: "the Notetaker stopped unexpectedly",
  RECORDING_PERMISSION_DENIED: "the host didn't allow recording",
  timeout_exceeded_waiting_room: "nobody let the Notetaker in",
  timeout_exceeded_noone_joined: "nobody joined the call",
  timeout_exceeded_everyone_left: "everyone had left the call",
  timeout_exceeded_in_call_not_recording: "the Notetaker wasn't allowed to record",
  timeout_exceeded_silence_detected: "the call was silent",
  timeout_exceeded_only_bots_in_call: "only other bots were left in the call",
  timeout_exceeded_recording_permission_denied: "the host didn't allow recording",
  meeting_not_found: "the meeting link didn't work",
  meeting_not_started: "the meeting hadn't started yet",
  meeting_password_incorrect: "the meeting needs a passcode",
  bot_kicked_from_waiting_room: "the Notetaker was turned away from the lobby",
  bot_kicked_from_call: "the Notetaker was removed from the call",
  recording_permission_denied: "the host didn't allow recording",
};

/** The reason a run failed, in words; one we have not met yet still says something true. */
export function failureWordsOf(reason: string | null): string {
  return (reason && FAILURE_WORDS[reason]) ?? "something went wrong";
}

/** The bot each calendar row shows, and the bots whose call is on no row any more yet still have something to give. */
export interface CallRecordings {
  ofMeeting: Map<string, NotetakerRecording>;
  unpaired: NotetakerRecording[];
}

/**
 * A bot belongs to the row it was sent for, or, where a calendar's re-read gave the call a new row, the row at the same
 * start with the same title; each bot to one row at most. A bot sent before its call moved is not that call's: it was
 * sent for the old time, and the server calls it back. Where several bots went to one call, the row shows the one that
 * matters most. A bot whose call left the calendar (replaced, renamed, deleted or long past) keeps a row of its own
 * while it is under way or holds what it recorded, so its transcript and changes are never out of reach.
 */
export function pairCallRecordings(meetings: Meeting[], recordings: NotetakerRecording[]): CallRecordings {
  const ofCall = new Map<string, NotetakerRecording[]>();
  const taken = new Set<string>();
  const claim = (matches: (meeting: Meeting, recording: NotetakerRecording) => boolean) => {
    for (const meeting of meetings) {
      for (const recording of recordings) {
        if (taken.has(recording.id) || !isSameStart(recording, meeting) || !matches(meeting, recording)) continue;
        taken.add(recording.id);
        ofCall.set(meeting.id, [...(ofCall.get(meeting.id) ?? []), recording]);
      }
    }
  };
  claim((meeting, recording) => recording.meetingId === meeting.id);
  claim((meeting, recording) => titleKeyOf(recording.title) === titleKeyOf(meeting.title));

  const ofMeeting = new Map<string, NotetakerRecording>();
  const unpaired: NotetakerRecording[] = [];
  for (const [meetingId, attempts] of ofCall) {
    const [shown, ...others] = [...attempts].sort((one, other) => rankOf(one) - rankOf(other));
    ofMeeting.set(meetingId, shown);
    unpaired.push(...others.filter(standsAlone));
  }
  unpaired.push(...recordings.filter((recording) => !taken.has(recording.id) && standsAlone(recording)));
  return { ofMeeting, unpaired };
}

/** A bot whose call is on no row, drawn as the call it was sent for. */
export function callOfRecording(recording: NotetakerRecording): Meeting {
  return {
    id: `${RECORDING_CALL_PREFIX}${recording.id}`,
    title: recording.title,
    startsAt: recording.startsAt,
    endsAt: recording.endsAt,
    ownerUserId: "",
    ownerName: null,
    joinUrl: null,
    videoProvider: null,
    viaLink: false,
    bookedInUncava: false,
    editable: false,
  };
}

/** A row drawn from a bot rather than from a calendar: nothing on it is the calendar's to change. */
export function isRecordingCall(meeting: Meeting): boolean {
  return meeting.id.startsWith(RECORDING_CALL_PREFIX);
}

const RECORDING_CALL_PREFIX = "recording:";

function isSameStart(recording: NotetakerRecording, meeting: Meeting): boolean {
  return new Date(recording.startsAt).getTime() === new Date(meeting.startsAt).getTime();
}

function titleKeyOf(title: string | null): string {
  return (title ?? "").trim().toLowerCase();
}

/** Under way first, then what was recorded, then a failure that can still give notes; a called-back bot last. */
function rankOf(recording: NotetakerRecording): number {
  if (["SCHEDULED", "JOINING", "RECORDING", "PROCESSING"].includes(recording.status)) return 0;
  if (recording.status === "DONE") return 1;
  if (recording.status === "FAILED" && (recording.retryable || recording.mayDownloadRecording)) return 2;
  if (recording.status === "FAILED") return 3;
  return 4;
}

/** A waiting bot is the server's to call back; a called-back one, or a failure with nothing left to give, is over. */
function standsAlone(recording: NotetakerRecording): boolean {
  return rankOf(recording) <= 2 && recording.status !== "SCHEDULED";
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

/** What puts a dot on a call's ⋯: the call being recorded, or something waiting on the consultant. */
export function callMarkOf(recording: NotetakerRecording | null): { tone: "recording" | "waiting"; words: string } | null {
  if (recording?.status === "RECORDING" && !recording.endRequested) return { tone: "recording", words: "recording" };
  if (recording?.status === "DONE" && recording.pendingSuggestions > 0) {
    return { tone: "waiting", words: `${countOf(recording.pendingSuggestions, "change")} to review` };
  }
  if (recording?.status === "FAILED" && recording.retryable) return { tone: "waiting", words: "no notes" };
  return null;
}
