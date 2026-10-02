import type { RecipientTokens } from "../api/sequenceApi";

/**
 * The editor's token bar and the renderer the preview and the review share — the same rules as the
 * server's `SequenceTokens`, so what a consultant reads is what Start freezes. A token nobody offers is
 * left as typed, so a misspelling shows rather than vanishing from the email.
 */

export interface SequenceTokenOption {
  token: string;
  tip: string;
  isAi?: boolean;
  isLink?: boolean;
}

export const OPENER_TOKEN = "{{opener}}";
export const BOOKING_LINK_TOKEN = "{{bookingLink}}";

const TOKEN = /\{\{\s*([A-Za-z]+)\s*\}\}/g;

export const SEQUENCE_TOKENS: SequenceTokenOption[] = [
  { token: "{{firstName}}", tip: "Their first name" },
  { token: "{{currentTitle}}", tip: "Their current title" },
  { token: "{{currentCompany}}", tip: "Their current employer" },
  { token: "{{positionTitle}}", tip: "This position's role title" },
  { token: "{{location}}", tip: "Their city" },
  { token: "{{senderFirstName}}", tip: "Your first name" },
  {
    token: BOOKING_LINK_TOKEN,
    tip: "Your booking page: they pick a free slot on your calendar, and their sequence stops",
    isLink: true,
  },
  {
    token: OPENER_TOKEN,
    tip: "One or two sentences AI drafts for each person from their profile. You review every one.",
    isAi: true,
  },
];

/** The bar's tokens; the booking link only where the mail service's plan offers booking pages. */
export function tokenOptions(bookingLinkOffered: boolean): SequenceTokenOption[] {
  return SEQUENCE_TOKENS.filter((option) => bookingLinkOffered || !option.isLink);
}

/** What the link reads as until the sender's first Start makes their real one. */
export const BOOKING_LINK_PLACEHOLDER = "(your booking link)";

export interface RenderedPart {
  text: string;
  isOpener: boolean;
  /** The sender's booking link, drawn as a link in the preview. */
  isLink?: boolean;
}

/** The template filled in, split so the opener can be highlighted where it landed. */
export function renderParts(template: string, tokens: RecipientTokens, opener: string | null): RenderedPart[] {
  const values: Record<string, string> = {
    firstName: tokens.firstName?.trim() ?? "",
    currentTitle: tokens.currentTitle?.trim() ?? "",
    currentCompany: tokens.currentCompany?.trim() ?? "",
    positionTitle: tokens.positionTitle?.trim() ?? "",
    location: tokens.location?.trim() ?? "",
    senderFirstName: tokens.senderFirstName?.trim() ?? "",
  };
  const parts: RenderedPart[] = [];
  let plain = "";
  let last = 0;
  for (const match of template.matchAll(TOKEN)) {
    plain += template.slice(last, match.index);
    last = (match.index ?? 0) + match[0].length;
    const name = match[1];
    if (name === "opener") {
      if (plain) parts.push({ text: plain, isOpener: false });
      plain = "";
      parts.push({ text: opener?.trim() ?? "", isOpener: true });
    } else if (name === "bookingLink" && tokens.bookingLink !== undefined) {
      if (plain) parts.push({ text: plain, isOpener: false });
      plain = "";
      parts.push({ text: tokens.bookingLink?.trim() ?? "", isOpener: false, isLink: true });
    } else {
      plain += Object.hasOwn(values, name) ? values[name] : match[0];
    }
  }
  plain += template.slice(last);
  if (plain) parts.push({ text: plain, isOpener: false });
  return parts;
}

export function render(template: string, tokens: RecipientTokens, opener: string | null): string {
  return renderParts(template, tokens, opener)
    .map((part) => part.text)
    .join("");
}

/** The first word of a full name, which is what a greeting uses. */
export function firstNameOf(fullName: string | null | undefined): string | null {
  const first = fullName?.trim().split(/\s+/)[0];
  return first ? first : null;
}
