import type {
  ConsentPurpose,
  ConsentRequestSkipReason,
  PersonConsent,
} from "../api/types";

/**
 * The words the Candidates screens use for consent requests (#1267), as `Candidates.dc.html` draws them, and the request
 * email word for word with `EmailTemplates.buildConsentRequestEmail` and `ConsentWording.askingOf`, so the preview
 * never promises what the email does not say.
 */

/** The purposes a person is asked about from the Candidates screens; a share is asked from its position. */
export const PERSONAL_PURPOSES = ["keep_on_file", "marketing"] as const satisfies readonly ConsentPurpose[];
export type PersonalPurpose = (typeof PERSONAL_PURPOSES)[number];

const ROW_LABELS: Record<ConsentPurpose, string> = {
  keep_on_file: "Keep on file",
  share_with_client: "Share with client",
  marketing: "Career and market updates",
};

export function consentRowLabel(purpose: ConsentPurpose): string {
  return ROW_LABELS[purpose];
}

export type ConsentTone = "idle" | "asked" | "given" | "refused" | "ended";

export interface ConsentRow {
  label: string;
  pill: string;
  tone: ConsentTone;
  line: string;
}

/** One drawer row: its pill and the line under the label. `day` formats an instant as the app's date. */
export function consentRowOf(
  purpose: ConsentPurpose,
  consent: PersonConsent | null,
  day: (instant: string | null) => string,
): ConsentRow {
  const label = consentRowLabel(purpose);
  if (!consent) return { label, pill: "Not asked", tone: "idle", line: "Never asked" };
  const via = consent.answeredVia === "by_hand" ? "recorded by hand" : "on their privacy page";
  switch (consent.status) {
    case "requested":
      return {
        label,
        pill: "Asked",
        tone: "asked",
        line: `Asked on ${day(consent.requestedAt)}${consent.requestedByName ? ` by ${consent.requestedByName}` : ""} · answer by ${day(consent.requestOpenUntil)}`,
      };
    case "given": {
      const renewing = !!consent.requestOpenUntil;
      const base = consent.validUntil ? `Until ${day(consent.validUntil)} · ${via}` : `Since ${day(consent.answeredAt)} · ${via}`;
      return {
        label,
        pill: renewing ? "Renewal asked" : "Given",
        tone: "given",
        line: renewing ? `${base} · renewal asked on ${day(consent.requestedAt)}` : base,
      };
    }
    case "declined":
      return { label, pill: "Declined", tone: "refused", line: `On ${day(consent.answeredAt)} · ${via}` };
    case "withdrawn":
      return { label, pill: "Withdrawn", tone: "refused", line: `On ${day(consent.answeredAt)} · ${via}` };
    case "expired":
      return {
        label,
        pill: "Expired",
        tone: "ended",
        line:
          consent.validUntil && !consent.requestOpenUntil && consent.answeredAt
            ? `Ended on ${day(consent.validUntil)}`
            : `Asked on ${day(consent.requestedAt)} · no answer in 30 days`,
      };
  }
}

const SKIPS: Record<ConsentRequestSkipReason, string> = {
  no_email: "No email on file",
  do_not_contact: "Do not contact",
  processing_restricted: "Processing restricted",
  suppressed: "On the suppression list",
  already_given: "Already agreed",
  recently_asked: "Asked in the last 14 days",
  daily_cap: "Over today's limit",
};

export function consentSkipLabel(reason: ConsentRequestSkipReason): string {
  return SKIPS[reason];
}

/** The dialog's checkbox for each purpose: what is asked, and how long it lasts. */
export function askedPurposeOf(
  purpose: PersonalPurpose,
  months: number,
  renewalDaysBefore: number,
): { label: string; sub: string } {
  if (purpose === "keep_on_file") {
    return {
      label: "Keep their details on file for future roles",
      sub:
        renewalDaysBefore > 0
          ? `For ${months} months, then asked again ${renewalDaysBefore} days before it ends`
          : `For ${months} months`,
    };
  }
  return { label: "Career and market updates", sub: "Recorded only: Uncava sends no updates" };
}

/** `ConsentWording.askingOf`: one bullet after "We'd like your permission to:". */
export function askingOf(purpose: PersonalPurpose, months: number): string {
  return purpose === "keep_on_file"
    ? `keep your details on file for future roles, for ${months} months`
    : "send you career and market updates";
}

export interface ConsentRequestPreview {
  subject: string;
  intro: string;
  asking: string[];
  closing: string;
}

/** A share asked about with the request: the role, and the client by name only when the firm names it. */
export interface SharePreview {
  roleTitle: string;
  clientName: string | null;
}

/** `ConsentWording.askingOf` for a share: "a confidential client" unless the client is named. */
export function shareAskingOf(share: SharePreview): string {
  return `share your profile with ${share.clientName ?? "a confidential client"} for a ${share.roleTitle} role`;
}

/**
 * `EmailTemplates.buildConsentRequestEmail`'s subject and paragraphs, in order; a share asked on its own is
 * `buildShareConsentRequestEmail`'s.
 */
export function consentRequestPreview(
  firm: string,
  sourcePhrase: string,
  purposes: readonly PersonalPurpose[],
  months: number,
  share: SharePreview | null = null,
): ConsentRequestPreview {
  if (share && purposes.length === 0) {
    return {
      subject: `May we share your profile for a ${share.roleTitle} role?`,
      intro: `${firm} is searching for a ${share.roleTitle} for ${share.clientName ?? "a confidential client"}. We hold some of your professional details, which came from ${sourcePhrase}.`,
      asking: [],
      closing: `We think your experience fits, and we'd like your permission to share your profile with ${share.clientName ?? "the client"}. It's your choice. If you don't answer, nothing changes.`,
    };
  }
  return {
    subject: `${firm} would like your permission`,
    intro: `${firm} is an executive search firm. We hold some of your professional details, which came from ${sourcePhrase}.`,
    asking: [...purposes.map((purpose) => askingOf(purpose, months)), ...(share ? [shareAskingOf(share)] : [])],
    closing: "Each is a separate choice, and you can say no to any of them. If you don't answer, nothing changes.",
  };
}
