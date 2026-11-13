import type { LawfulBasis, PersonPrivacy, PrivacyNoticeChannel, PrivacyNoticeSkipReason } from "../api/types";

/** The words the Candidates screens use for a person's privacy record (#923), as `Candidates.dc.html` draws them. */

export const LAWFUL_BASES: readonly { value: LawfulBasis; label: string }[] = [
  { value: "legitimate_interest", label: "Legitimate interest" },
  { value: "consent", label: "Consent" },
  { value: "contract", label: "Contract" },
];

export function lawfulBasisLabel(basis: LawfulBasis): string {
  return LAWFUL_BASES.find((entry) => entry.value === basis)?.label ?? basis;
}

const CHANNELS: Record<PrivacyNoticeChannel, string> = {
  privacy_email: "privacy email",
  outreach_email: "first outreach email",
  marked_by_hand: "marked by hand",
};

export function noticeChannelLabel(channel: PrivacyNoticeChannel): string {
  return CHANNELS[channel];
}

const SKIPS: Record<PrivacyNoticeSkipReason, string> = {
  no_email: "No email on file",
  do_not_contact: "Do not contact",
  suppressed: "On the suppression list",
  processing_restricted: "Processing restricted",
  recently_notified: "Sent one in the last 14 days",
  daily_cap: "Today's notices are used up",
};

export function skipReasonLabel(reason: PrivacyNoticeSkipReason): string {
  return SKIPS[reason];
}

/** Today in the browser's own calendar, as the `YYYY-MM-DD` a date input reads and the server takes. */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export function isNotified(privacy: PersonPrivacy | null | undefined): boolean {
  return !!privacy?.notifiedAt;
}
