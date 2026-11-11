import type { RetentionErased, SuppressionKind, SuppressionReason } from "../api/dataRetentionApi";

/**
 * Settings → Data & retention's words (`Settings.dc.html`, `data`), worked out from the period alone so every
 * sentence is tested without a screen. Null months is "never delete".
 */

export const NEVER = "never";

export function periodLabel(months: number | null): string {
  return months === null ? "Never delete" : `${months} months`;
}

export function retentionNote(months: number | null): string {
  if (months === null) {
    return "Retention is off: candidates are kept until someone deletes them. You can still delete anyone from Candidates.";
  }
  const period = `${months} months`;
  return `Nobody is deleted without review. Whoever passes ${period} waits under Due for deletion until an admin deletes them or keeps them for another ${period}.`;
}

export function dueSubtitle(savedMonths: number | null): string {
  if (savedMonths === null) return "Retention is off, so nobody becomes due.";
  const period = `${savedMonths} months`;
  return `Untouched for ${period} and in no live position. Delete them, or keep them for another ${period}.`;
}

export function dueEmptyBody(savedMonths: number | null): string {
  if (savedMonths === null) {
    return "Retention is off, so nobody becomes due. Choose a period above to start reviewing.";
  }
  return `Candidates untouched for ${savedMonths} months and in no live position appear here for you to review. Nothing is deleted until you say so.`;
}

export function savedToast(months: number | null): string {
  return months === null ? "Retention turned off" : `Candidates are now kept for ${months} months untouched`;
}

export function peopleCount(count: number): string {
  return count === 1 ? "1 person" : `${count} people`;
}

/** "Dina and Karim"; "Dina, Karim and Rami". */
export function namesSentence(names: readonly string[]): string {
  if (names.length <= 2) return names.join(" and ");
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

export function deleteTitle(count: number): string {
  return count === 1 ? "Delete 1 candidate permanently?" : `Delete ${count} candidates permanently?`;
}

/** What a delete did, including who it left for the next review and why. */
export function erasedToast(result: RetentionErased): string {
  const deleted =
    result.erased === 1 ? "1 candidate deleted permanently" : `${result.erased} candidates deleted permanently`;
  const left: string[] = [];
  if (result.noLongerDue > 0) left.push(`${peopleCount(result.noLongerDue)} no longer due`);
  if (result.sendInFlight > 0) left.push(`${peopleCount(result.sendInFlight)} still being emailed`);
  return left.length === 0 ? deleted : `${deleted} · ${left.join(", ")} kept`;
}

/** "14 Aug 2024" in the viewer's zone. */
export function dayOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}

const LAST_ACTIVITY: Record<string, string> = {
  RETENTION_KEPT: "Kept by an admin",
  ADDED_TO_POOL: "Added",
  MAPPED: "Added to a position",
  UNMAPPED: "Removed from a position",
  STATUS_CHANGED: "Status changed",
  TRIAGE_STATUS_CHANGED: "Stage changed",
  NOTE_ADDED: "Note added",
  NOTE_EDITED: "Note edited",
  NOTE_REMOVED: "Note removed",
  NOTE_RESTORED: "Note restored",
  EMAIL_SENT: "Email sent",
  EMAIL_REPLIED: "Replied",
  OUTREACH_ENROLLED: "Added to a sequence",
  OUTREACH_STOPPED: "Sequence stopped",
  MEETING_BOOKED: "Call booked",
  MEETING_RESCHEDULED: "Call moved",
  MEETING_CANCELLED: "Call cancelled",
  PROFILE_EDITED: "Profile edited",
  CONTACTS_EDITED: "Contacts edited",
  CONTACT_FOUND: "Contact looked up",
  RESEARCHED: "Researched",
  DOCUMENT_ADDED: "Document added",
  DOCUMENT_VERSION_ADDED: "Document added",
  TASK_CREATED: "Task added",
  TASK_COMPLETED: "Task done",
};

/** The last thing that happened to someone, in a word or two; anything else is an edit to their record. */
export function lastActivityPhrase(kind: string | null): string {
  if (kind === null) return "Added";
  return LAST_ACTIVITY[kind] ?? "Record updated";
}

export function suppressionKindLabel(kind: SuppressionKind): string {
  return kind === "linkedin" ? "LinkedIn" : "Email";
}

export function suppressionReasonLabel(reason: SuppressionReason): string {
  switch (reason) {
    case "erased":
      return "Erased";
    case "objected":
      return "Objected";
    case "manual":
      return "Added by an admin";
  }
}

export function suppressionRemoveBody(reason: SuppressionReason): string {
  return reason === "erased"
    ? "This entry came from a permanent deletion. Once it goes, anyone can add that person again — from the browser plugin, an import or by hand."
    : "Anyone can then add this person again — from the browser plugin, an import or by hand.";
}

/** The client's half of the server's rule: a member profile link, `https://` optional. */
export function linkedinProfileProblem(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  return /linkedin\.com\/in\/[^/?#\s]+/i.test(trimmed)
    ? null
    : "Paste a LinkedIn profile link, such as linkedin.com/in/name";
}

/** "1.4 GB", "318 MB", "54 KB": an export runs from a few kilobytes to gigabytes. */
export function exportSizeLabel(bytes: number): string {
  const units = ["KB", "MB", "GB"];
  let size = bytes / 1024;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size >= 10 || unit === 0 ? Math.max(1, Math.round(size)) : Number(size.toFixed(1))} ${units[unit]}`;
}

/** "5 Oct 2026, 14:09" in the viewer's zone. */
export function momentOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return `${dayOf(isoInstant)}, ${at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
}

/** "expires in 7 days", or "expires within a day" for the last of them. */
export function expiresInPhrase(isoInstant: string, now: Date = new Date()): string {
  const left = new Date(isoInstant).getTime() - now.getTime();
  if (left < 86_400_000) return "expires within a day";
  const days = Math.ceil(left / 86_400_000);
  return `expires in ${days} ${days === 1 ? "day" : "days"}`;
}
