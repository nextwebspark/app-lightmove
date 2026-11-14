import type { PrivacyCategory, PrivacyPage } from "../api/privacyPageApi";

/** The public privacy page's words (`Privacy.dc.html`): the firm speaks as controller, Uncava only as the software. */

const CATEGORIES: Record<PrivacyCategory, string> = {
  name_and_role: "Name and current role",
  career: "Career history",
  location: "Where you're based",
  work_email: "Work email address",
  personal_email: "Personal email address",
  phone: "Phone number",
  linkedin: "LinkedIn profile",
  compensation: "Pay and notice period",
  background: "Background, such as nationality",
  notes: "Our consultants' notes",
  documents: "Documents, such as a CV",
  assessments: "Our assessments against a role",
};

export function categoryLabel(category: PrivacyCategory): string {
  return CATEGORIES[category] ?? category;
}

const SOURCES: Record<string, string> = {
  manual: "Our own research.",
  csv: "A list our team put together from its own research.",
  extension: "Your public professional profile.",
  ai_sourced: "A search of public professional profiles.",
  people_search: "A search of public professional profiles.",
};

export function sourceSentence(source: string | null): string {
  return (source && SOURCES[source]) || "Our own research.";
}

export function basisSentence(basis: PrivacyPage["lawfulBasis"]): string {
  switch (basis) {
    case "consent":
      return "Your consent. You can withdraw it at any time.";
    case "contract":
      return "A contract with you.";
    default:
      return "Our legitimate interest in finding candidates for senior roles. We do not sell your details.";
  }
}

/** `optOut` is offered only by an outreach email's stop link (`?action=opt-out`, #924), never in the list. */
export type PrivacyChoice = "object" | "copy" | "deletion" | "optOut";

export const CHOICES: readonly { key: PrivacyChoice; label: string; detail: string }[] = [
  { key: "object", label: "Don't contact me again", detail: "We stop all contact and will not add your details again." },
  {
    key: "copy",
    label: "Send me a copy of my data",
    detail: "Our privacy team sends you everything we hold, within one month.",
  },
  { key: "deletion", label: "Delete my data", detail: "Our privacy team deletes what we hold, within one month." },
];

export function confirmationOf(
  choice: PrivacyChoice,
  firm: string,
): { title: string; body: string; cta: string; back: string } {
  switch (choice) {
    case "optOut":
      return {
        title: `Stop emails from ${firm === "We" ? "us" : firm}?`,
        body: "You won't receive any more emails from our consultants, and we won't add your details again.",
        cta: "Stop emails",
        back: "See all your choices",
      };
    case "object":
      return {
        title: "Stop all contact from us?",
        body: `${firm} will not contact you again, by email or any other way, and will not add your details again. We keep only what we need to remember not to contact you.`,
        cta: "Don't contact me again",
        back: "Back",
      };
    case "copy":
      return {
        title: "Ask for a copy of your data?",
        body: "We will send a copy of everything we hold about you to the email address we have for you, within one month.",
        cta: "Send the request",
        back: "Back",
      };
    case "deletion":
      return {
        title: "Ask us to delete your data?",
        body: "We will delete what we hold about you within one month, unless the law requires us to keep part of it, and we will tell you when it is done. We keep only a note that you asked, so we do not add your details again.",
        cta: "Send the request",
        back: "Back",
      };
  }
}

export function doneOf(choice: PrivacyChoice, firm: string): { title: string; body: string } {
  switch (choice) {
    case "optOut":
      return {
        title: "You won't receive more emails from us",
        body: `${firm} has recorded that you want no more emails. Nobody here will contact you, and your details will not be added again.`,
      };
    case "object":
      return {
        title: "You won't hear from us again",
        body: `${firm} has recorded your objection. Nobody here will contact you, and your details will not be added again.`,
      };
    case "copy":
      return {
        title: "Your request has been sent",
        body: `${firm}'s privacy team has your request for a copy of your data, and will reply within one month.`,
      };
    case "deletion":
      return {
        title: "Your request has been sent",
        body: `${firm}'s privacy team has your request to delete your data, and will reply within one month.`,
      };
  }
}

export function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return ((words[0]?.[0] ?? "") + (words[1]?.[0] ?? "")).toUpperCase();
}
