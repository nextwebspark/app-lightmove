import type { OutreachRun, RunStatus, StopReason } from "../api/runApi";

/** A run's chip, worded and coloured as `Outreach.dc.html`'s `STATE` table. */
export const RUN_STATES: Record<RunStatus, { label: string; className: string }> = {
  SCHEDULED: { label: "Scheduled", className: "bg-u-raised text-u-text2" },
  ACTIVE: { label: "In sequence", className: "bg-u-signal-tint text-u-signal" },
  REPLIED: { label: "Replied", className: "bg-u-direct-tint text-u-direct" },
  BOUNCED: { label: "Bounced", className: "bg-u-offlimits-tint text-u-offlimits" },
  STOPPED: { label: "Stopped", className: "bg-u-sunken text-u-text3" },
  COMPLETED: { label: "Completed", className: "bg-u-raised text-u-text3" },
  BOOKED: { label: "Booked a call", className: "bg-u-direct-tint text-u-direct" },
};

/** Why a run stopped short, as the table's note under the chip and the drawer say it. */
export const STOP_NOTES: Record<StopReason, string> = {
  MANUAL: "Stopped by hand",
  DO_NOT_CONTACT: "Marked do not contact",
  LEFT_THE_RUNNING: "No longer in the running",
  UNMAPPED: "Removed from the position",
  ADDRESS_REMOVED: "Address removed",
  MAILBOX_INACTIVE: "Mailbox disconnected",
  MAILBOX_MOVED: "Mailbox reconnected",
  BOOKING_LINK_UNAVAILABLE: "Booking link unavailable",
  SEND_FAILED: "The mail service refused it",
  SEND_UNCERTAIN: "A send may not have gone",
};

export type RunFilter = "all" | "inFlight" | "replied" | "booked" | "bounced" | "stopped";

export const RUN_FILTERS: { key: RunFilter; label: string }[] = [
  { key: "all", label: "All" },
  { key: "inFlight", label: "In flight" },
  { key: "replied", label: "Replied" },
  { key: "booked", label: "Booked a call" },
  { key: "bounced", label: "Bounced" },
  { key: "stopped", label: "Stopped" },
];

export function isLive(run: Pick<OutreachRun, "status">): boolean {
  return run.status === "SCHEDULED" || run.status === "ACTIVE";
}

export function matchesFilter(run: OutreachRun, filter: RunFilter): boolean {
  switch (filter) {
    case "all":
      return true;
    case "inFlight":
      return isLive(run);
    case "replied":
      return run.status === "REPLIED";
    case "booked":
      return run.status === "BOOKED";
    case "bounced":
      return run.status === "BOUNCED";
    case "stopped":
      return run.status === "STOPPED";
  }
}

/** The mono note beside a run's chip: when it was answered, or why it ended. */
export function runNoteOf(run: OutreachRun): string {
  switch (run.status) {
    case "REPLIED":
      return run.endedAt ? sendTimeOf(run.endedAt).replace(" · ", " ") : "";
    case "BOOKED":
      return run.endedAt ? `Booked ${sendTimeOf(run.endedAt).replace(" · ", " ")}` : "";
    case "BOUNCED":
      return "Address rejected";
    case "STOPPED":
      return run.stopReason ? STOP_NOTES[run.stopReason] : "";
    case "SCHEDULED":
    case "ACTIVE":
    case "COMPLETED":
      return "";
  }
}

/** "Fri 3 Oct · 09:00", in the viewer's own zone. */
export function sendTimeOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  const day = at.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
  const time = at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  return `${day.replace(",", "")} · ${time}`;
}
