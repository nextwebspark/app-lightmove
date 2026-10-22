import type { NotetakerEndReason, NotetakerMeeting, NotetakerPlatform } from "../api/notetakerApi";

/** Where a call's bot has got to, as the drawer draws it (`Position.dc.html`, `NT_STATES`). */
export type NotetakerChipState =
  | "joining"
  | "waiting"
  | "recording"
  | "backup"
  | "leaving"
  | "processing"
  | "ready"
  | "partial"
  | "unheard"
  | "notesFailed"
  | "removed"
  | "failed"
  | "skipped";

export type NotetakerChipTone = "neutral" | "waiting" | "live" | "working" | "done" | "partial" | "problem";

export interface NotetakerChip {
  state: NotetakerChipState;
  label: string;
  tone: NotetakerChipTone;
  /** What the hover says. */
  title: string;
  /** The bot is (or may be about to be) in the call, so Remove notetaker is offered. */
  removable: boolean;
  /** The note is filed where this drawer may read its transcript. */
  readable: boolean;
  /** Its note could not be made; a consultant may press Try again. */
  retryable: boolean;
  /** Its note is filed on another position, which is never named. */
  elsewhere: boolean;
}

const END_REASONS: Record<NotetakerEndReason, string> = {
  NOT_ADMITTED: "Nobody admitted the notetaker in time.",
  JOIN_BLOCKED: "The call's host doesn't let guests join.",
  CONSENT_NOT_SHOWN: "It couldn't say in the chat that it was recording, so it left without recording.",
  DEVICES_NOT_OFF: "It couldn't be sure its microphone and camera were off, so it never asked to join.",
  LAUNCH_FAILED: "The notetaker couldn't be started.",
  LAUNCH_TIMEOUT: "The notetaker didn't start in time.",
  CRASHED: "The notetaker stopped before it heard anyone.",
  HEARTBEAT_LOST: "The notetaker stopped responding before it heard anyone.",
  DO_NOT_CONTACT: "Someone on the call is marked do not contact, so the notetaker stayed away.",
  PROCESSING_FAILED: "The call was recorded, but its transcript or note couldn't be made.",
};

/**
 * Why a bot couldn't record. On Teams a chat it could not write in is most often the organiser's tenant turning chat
 * off for guests, which the consultant cannot fix from here, so the sentence says so.
 */
function failedReasonOf(meeting: NotetakerMeeting): string {
  if (meeting.endReason === "CONSENT_NOT_SHOWN" && meeting.platform === "MICROSOFT_TEAMS") {
    return "This meeting doesn't let guests chat, so the notetaker couldn't say it was recording and left without recording.";
  }
  return END_REASONS[meeting.endReason ?? "NOT_ADMITTED"];
}

const PLATFORM_NAMES: Record<NotetakerPlatform, string> = {
  GOOGLE_MEET: "Google Meet",
  MICROSOFT_TEAMS: "Microsoft Teams",
  ZOOM: "Zoom",
};

/** The chip for a call a bot was sent to (or skipped); null while none has been. */
export function notetakerChipOf(meeting: NotetakerMeeting, timeOf: (instant: string) => string): NotetakerChip | null {
  const chip = (
    state: NotetakerChipState,
    label: string,
    tone: NotetakerChipTone,
    title: string,
    flags: Partial<Pick<NotetakerChip, "removable" | "readable" | "retryable" | "elsewhere">> = {},
  ): NotetakerChip => ({
    state,
    label,
    tone,
    title,
    removable: false,
    readable: false,
    retryable: false,
    elsewhere: false,
    ...flags,
  });
  switch (meeting.status) {
    case null:
      return null;
    case "LAUNCHING":
      return chip("joining", "Joining", "neutral", "The notetaker is opening the call.", { removable: true });
    case "WAITING_ROOM":
      return chip("waiting", "Waiting to be admitted", "waiting", "Someone in the call has to let the notetaker in.", {
        removable: true,
      });
    case "RECORDING": {
      const since = meeting.joinedAt ? `Recording since ${timeOf(meeting.joinedAt)}. ` : "";
      return meeting.recorder === "RECALL"
        ? chip("backup", "Recording (backup)", "live", "Our notetaker couldn't get in, so the backup recorder joined instead.", {
            removable: true,
          })
        : chip("recording", "Recording", "live", `${since}It said in the chat that it is recording.`, { removable: true });
    }
    case "REMOVE_REQUESTED":
      return chip("leaving", "Leaving…", "neutral", "Asked to leave. It goes within a few seconds, and nothing it recorded is kept.");
    case "PROCESSING":
      return chip("processing", "Processing", "working", "Writing the transcript and the meeting note. It usually takes a few minutes.");
    case "DONE": {
      const filed = { readable: meeting.transcriptHere, elsewhere: meeting.notesElsewhere };
      if (!meeting.transcriptHere && !meeting.notesElsewhere) {
        return chip("unheard", "No note", "neutral", "Nobody was heard on the call, so no note was filed.");
      }
      return meeting.recordedUntil
        ? chip(
            "partial",
            "Notes ready · partial",
            "partial",
            `The notetaker stopped at ${timeOf(meeting.recordedUntil)}, before the call ended. The note covers what it heard.`,
            filed,
          )
        : chip("ready", "Notes ready", "done", "The meeting note is on Notes, with its transcript.", filed);
    }
    case "FAILED":
      return meeting.endReason === "PROCESSING_FAILED"
        ? chip("notesFailed", "Notes failed", "problem", END_REASONS.PROCESSING_FAILED, { retryable: true })
        : chip("failed", "Couldn't record", "neutral", failedReasonOf(meeting));
    case "REMOVED":
      return chip("removed", "Removed", "neutral", "Removed from the call. Nothing it recorded was kept.");
    case "SKIPPED":
      return chip("skipped", "Not joined", "neutral", END_REASONS[meeting.endReason ?? "DO_NOT_CONTACT"]);
  }
}

/** "Google Meet", or "Google Meet and Microsoft Teams": the calls this deployment's bot joins; null for none. */
export function platformListOf(platforms: readonly NotetakerPlatform[]): string | null {
  const names = platforms.map((platform) => PLATFORM_NAMES[platform]);
  if (names.length === 0) return null;
  return names.length === 1 ? names[0] : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

/** "The notetaker joins Google Meet calls.", under a call it never joins. */
export function joinsLineOf(platforms: readonly NotetakerPlatform[]): string {
  const listed = platformListOf(platforms);
  return listed ? `The notetaker joins ${listed} calls.` : "The notetaker doesn't join calls yet.";
}
