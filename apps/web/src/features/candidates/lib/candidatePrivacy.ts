import type {
  CandidateSource,
  LawfulBasis,
  PersonPrivacy,
  PrivacyNoticeChannel,
  PrivacyNoticeSkipReason,
} from "../api/types";

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

/** `PrivacyNoticeWording.sourceOf`, word for word. */
const NOTICE_SOURCES: Record<CandidateSource, string> = {
  manual: "our own research",
  csv: "a list our team imported from its own research",
  extension: "your public professional profile",
  ai_sourced: "a search of public professional profiles",
  people_search: "a search of public professional profiles",
};

/** `PrivacyNoticeWording.basisOf`, word for word. */
const NOTICE_BASES: Record<LawfulBasis, string> = {
  legitimate_interest: "our legitimate interest in finding people for senior roles",
  consent: "your consent",
  contract: "a contract with you",
};

/**
 * The notice email's two paragraphs exactly as `EmailTemplates.buildPrivacyNoticeEmail` writes them, so the preview
 * never promises what the email does not say. A bulk send has no one person's source or basis, so each is named in
 * brackets rather than guessed.
 */
export function noticePreviewParagraphs(
  firm: string,
  source: CandidateSource | null | undefined,
  basis: LawfulBasis | null | undefined,
): [string, string] {
  const from = source ? NOTICE_SOURCES[source] : "[where their details came from]";
  const why = basis ? NOTICE_BASES[basis] : "[their lawful basis]";
  return [
    `${firm} holds some of your professional details, such as your name, career history and work contact details, which came from ${from}. We hold them to consider you for senior roles, on the basis of ${why}.`,
    "You can object, ask for a copy of what we hold, or ask us to delete it at any time.",
  ];
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
