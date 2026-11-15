import type { PrivacyPageConsentPurpose } from "../api/privacyPageApi";

/** The consent request and manage screens' words (`Privacy.dc.html`, #1267); the firm speaks, Uncava is the software. */

const TITLES: Record<PrivacyPageConsentPurpose, string> = {
  keep_on_file: "Keep your details on file for future roles",
  share_with_client: "Share your profile with a client",
  marketing: "Career and market updates",
};

/** A purpose's heading on the request page, and its name beside an answer. */
export function consentTitleOf(purpose: PrivacyPageConsentPurpose): string {
  return TITLES[purpose];
}

export function requestTitleOf(firm: string): string {
  return `${firm} asks for your permission`;
}

export function requestLeadOf(firm: string): string {
  return `${firm}, an executive search firm, holds some of your professional details. We would like your permission for the uses below. Each is a separate choice.`;
}

export const REQUEST_SILENCE =
  "You don't have to answer. If you don't, nothing changes: we keep holding your details only as our privacy notice says.";

export function renewalTitleOf(endsOn: string): string {
  return `Your consent to keep your details ends on ${endsOn}`;
}

export function renewalLeadOf(firm: string, months: number): string {
  return `Would you like ${firm} to keep your details on file for future roles for another ${months} months?`;
}

export function renewalBlockTitleOf(months: number): string {
  return `Keep your details on file for another ${months} months`;
}

export function renewalSilenceOf(endsOn: string): string {
  return `You don't have to answer. If you don't, your consent ends on ${endsOn} and we keep your details only as our privacy notice says.`;
}

export function answeredBodyOf(firm: string, agreedToAnything: boolean): string {
  return agreedToAnything
    ? `${firm} has recorded them. We've emailed you a link to change your mind at any time.`
    : `${firm} has recorded them. We've emailed you a confirmation.`;
}

export function closedBodyOf(firm: string, closedOn: string): string {
  return `It was open for 30 days and closed on ${closedOn}. Nothing changed: you didn't agree to anything, and ${firm} holds your details only as its privacy notice says.`;
}

export function manageLeadOf(firm: string): string {
  return `What you've agreed to with ${firm}. You can withdraw any of them, and it takes effect at once.`;
}

export function withdrawConfirmationOf(purpose: PrivacyPageConsentPurpose): { title: string; body: string } {
  switch (purpose) {
    case "keep_on_file":
      return {
        title: "Withdraw your consent to keep your details on file?",
        body: "We will no longer keep your details for future roles on the strength of your consent. What we hold is then kept only as our privacy notice says. You can agree again later.",
      };
    case "marketing":
      return {
        title: "Withdraw your consent to career and market updates?",
        body: "We will stop sending you updates. You can agree again later.",
      };
    case "share_with_client":
      return {
        title: "Withdraw your consent to share your profile?",
        body: "We will no longer share your profile with the client on the strength of your consent. You can agree again later.",
      };
  }
}

export function withdrawnBodyOf(firm: string): string {
  return `${firm} has recorded it and will no longer rely on it. We have emailed you a confirmation.`;
}
