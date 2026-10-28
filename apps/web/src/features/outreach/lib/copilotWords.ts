import type { Copilot, CoverageState, RecordGap } from "../api/notetakerApi";

/** What each of the record's gaps is called on the copilot's checklist and in the plan. */
export const GAP_LABEL: Record<RecordGap, string> = {
  NOTICE: "Notice",
  PACKAGE: "Package",
  LOCATION: "Location",
  RELOCATION: "Relocation",
  MOTIVATION: "Motivation",
  EXPECTED_PACKAGE: "Expected",
};

export const COVERAGE_LABEL: Record<CoverageState, string> = {
  NOT_YET: "Not yet",
  TOUCHED: "Touched, no example",
  EVIDENCED: "Evidenced",
};

/** Whether the call is still on for the copilot: the bot is in it, or on its way. */
export function isCallOn(copilot: Copilot): boolean {
  return copilot.status === "SCHEDULED" || copilot.status === "JOINING" || copilot.status === "RECORDING";
}

export function isWaitingToJoin(copilot: Copilot): boolean {
  return copilot.status === "SCHEDULED" || copilot.status === "JOINING";
}

/**
 * The one line under the header that says why coverage is not current, if they are not. Null while all is well,
 * and for a call that is over: the ended view says that itself.
 */
export function copilotNoticeOf(copilot: Copilot): string | null {
  // Every copilot call ends with its feed stopped: once the call is over, the ended view speaks instead.
  if (!isCallOn(copilot)) return null;
  if (copilot.liveStatus === "STOPPED") {
    return `Copilot stopped by ${copilot.stoppedByName ?? "a teammate"}. The recording continues and notes are filed after the call.`;
  }
  if (copilot.liveStatus === "FEED_STOPPED") {
    return "Live transcript stopped. Notes will still be filed if the recording continues.";
  }
  switch (copilot.coach) {
    case "PAUSED":
      return "Coverage paused. Retrying when more is said.";
    case "GUARDED":
      return "Something said reads like an instruction to the AI, so it was skipped.";
    case "CAPPED":
      return "Coverage stopped for this call: it reached its limit.";
    default:
      return null;
  }
}

/** What stayed open when the call ended: competencies short of evidence, and gaps nobody spoke to. */
export function uncoveredOf(copilot: Copilot): string[] {
  return [
    ...copilot.coverage.filter((line) => line.state !== "EVIDENCED").map((line) => line.competency),
    ...copilot.gaps.filter((gap) => !gap.heard).map((gap) => GAP_LABEL[gap.gap]),
  ];
}

/** Time into the call from when it started, as the transcript sheet writes it. */
export function elapsedSince(startsAt: string, now: number): string {
  const seconds = Math.max(0, Math.floor((now - new Date(startsAt).getTime()) / 1000));
  const minutes = Math.floor(seconds / 60);
  const pad = (value: number) => String(value).padStart(2, "0");
  return minutes >= 60
    ? `${Math.floor(minutes / 60)}:${pad(minutes % 60)}:${pad(seconds % 60)}`
    : `${pad(minutes)}:${pad(seconds % 60)}`;
}
