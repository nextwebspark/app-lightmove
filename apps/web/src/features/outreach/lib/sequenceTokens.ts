import type { RecipientTokens } from "../api/sequenceApi";

/**
 * The editor's token bar and the renderer the preview and the review share — the same rules as the
 * server's `SequenceTokens`, so what a consultant reads is what Start freezes. `{{name|fallback}}` reads the
 * fallback where the person has no value. A token nobody offers is left as typed, so a misspelling shows
 * rather than vanishing from the email.
 */

export interface SequenceTokenOption {
  token: string;
  tip: string;
  isAi?: boolean;
  isLink?: boolean;
}

export const OPENER_TOKEN = "{{opener}}";
export const BOOKING_LINK_TOKEN = "{{bookingLink}}";

/**
 * `{{name}}` or `{{name|fallback}}`, the server's grammar. The pipe may arrive escaped (`\|`): the notes editor's
 * Markdown serializer escapes every pipe in text.
 */
const TOKEN = /\{\{\s*([A-Za-z]+)\s*(?:\\?\|([^{}|\n]{1,60}))?\s*\}\}/g;

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

/** An email template's and the composer's bar: a sequence's, in the same order, less the opener a run writes per person. */
export function templateTokenOptions(bookingLinkOffered: boolean): SequenceTokenOption[] {
  return tokenOptions(bookingLinkOffered).filter((option) => !option.isAi);
}

/** No email holds a signature in its words: Include signature adds the sender's own when it goes, or when a run starts. */
export const SIGNATURE_NOT_IN_WORDS = "Leave {{signature}} out: Include signature adds yours when you send.";

export function holdsSignature(text: string | null | undefined): boolean {
  return tokenUsesIn(text ?? "").some((use) => use.name === "signature");
}

/** The names a token option inserts, `{{firstName}}` → `firstName`. */
export function tokenNameOf(option: SequenceTokenOption): string {
  return option.token.slice(2, -2);
}

export interface TokenUse {
  name: string;
  /** Trimmed and collapsed; null where none was written. */
  fallback: string | null;
}

/** Every token the text holds, in order. */
export function tokenUsesIn(text: string | null | undefined): TokenUse[] {
  if (!text) return [];
  return [...text.matchAll(TOKEN)].map((match) => ({ name: match[1], fallback: fallbackOf(match[2]) }));
}

/** The tokens the text uses that the vocabulary does not hold, in order of first use. */
export function unknownIn(text: string | null | undefined, vocabulary: readonly string[]): string[] {
  return [...new Set(tokenUsesIn(text).map((use) => use.name).filter((name) => !vocabulary.includes(name)))];
}

/** The known tokens the text uses with no value for this person and no fallback, in order of first use. */
export function unfilledIn(text: string | null | undefined, tokens: RecipientTokens): string[] {
  return emptyTokensIn(text, tokens, false);
}

/** The known tokens the text uses with no value for this person, where a fallback stands in, in order of first use. */
export function fallbacksIn(text: string | null | undefined, tokens: RecipientTokens): string[] {
  return emptyTokensIn(text, tokens, true);
}

function emptyTokensIn(text: string | null | undefined, tokens: RecipientTokens, withFallback: boolean): string[] {
  const values = valuesOf(tokens);
  return [
    ...new Set(
      tokenUsesIn(text)
        .filter((use) => values[use.name] === "" && (use.fallback !== null) === withFallback)
        .map((use) => use.name),
    ),
  ];
}

export interface TokenGaps {
  /** Nothing on file and no fallback: it goes out blank. */
  blanks: string[];
  /** Nothing on file, and a fallback stands in everywhere it is used. */
  fallbacks: string[];
}

/** What this person has nothing on file for across several texts, such as a sequence's subject and every step. */
export function tokenGapsIn(texts: readonly (string | null | undefined)[], tokens: RecipientTokens): TokenGaps {
  const blanks = [...new Set(texts.flatMap((text) => unfilledIn(text, tokens)))];
  const fallbacks = [...new Set(texts.flatMap((text) => fallbacksIn(text, tokens)))].filter(
    (name) => !blanks.includes(name),
  );
  return { blanks, fallbacks };
}

/** A subject: every value on one line, as the server writes it. */
export function renderLine(template: string, tokens: RecipientTokens): string {
  return render(template, tokens, null).replace(/\s+/g, " ").trim();
}

/** The notes editor's serializer escapes ASCII punctuation in text, a fallback's included; read back as typed. */
const MARKDOWN_ESCAPE = /\\([!-/:-@[-`{-~])/g;

function fallbackOf(typed: string | undefined): string | null {
  const fallback = typed?.replace(MARKDOWN_ESCAPE, "$1").replace(/\s+/g, " ").trim() ?? "";
  return fallback === "" ? null : fallback;
}

function valuesOf(tokens: RecipientTokens): Record<string, string> {
  return {
    firstName: tokens.firstName?.trim() ?? "",
    currentTitle: tokens.currentTitle?.trim() ?? "",
    currentCompany: tokens.currentCompany?.trim() ?? "",
    positionTitle: tokens.positionTitle?.trim() ?? "",
    location: tokens.location?.trim() ?? "",
    senderFirstName: tokens.senderFirstName?.trim() ?? "",
  };
}

/** What the link reads as until the sender's first Start makes their real one. */
export const BOOKING_LINK_PLACEHOLDER = "(your booking link)";

export interface RenderedPart {
  text: string;
  isOpener: boolean;
  /** The sender's booking link, drawn as a link in the preview. */
  isLink?: boolean;
  /** A variable with nothing on file and no fallback: its text is empty, as the server sends it; `token` names it. */
  isBlank?: boolean;
  /** A variable with nothing on file whose fallback stands in: its text is the fallback; `token` names it. */
  isFallback?: boolean;
  token?: string;
}

/** The template filled in, split so the opener and the link can be drawn where they landed. */
export function renderParts(template: string, tokens: RecipientTokens, opener: string | null): RenderedPart[] {
  const values = valuesOf(tokens);
  const parts: RenderedPart[] = [];
  let plain = "";
  let last = 0;
  for (const match of template.matchAll(TOKEN)) {
    plain += template.slice(last, match.index);
    last = (match.index ?? 0) + match[0].length;
    const name = match[1];
    const fallback = fallbackOf(match[2]);
    if (name === "opener") {
      if (plain) parts.push({ text: plain, isOpener: false });
      plain = "";
      parts.push({ text: opener?.trim() || fallback || "", isOpener: true });
    } else if (name === "bookingLink" && tokens.bookingLink !== undefined) {
      if (plain) parts.push({ text: plain, isOpener: false });
      plain = "";
      const link = tokens.bookingLink?.trim() ?? "";
      // As the server sends it: with no link, a fallback stands in.
      parts.push(
        !link && fallback
          ? { text: fallback, isOpener: false, isFallback: true, token: name }
          : { text: link, isOpener: false, isLink: true },
      );
    } else if (Object.hasOwn(values, name) && !values[name]) {
      if (plain) parts.push({ text: plain, isOpener: false });
      plain = "";
      parts.push(
        fallback
          ? { text: fallback, isOpener: false, isFallback: true, token: name }
          : { text: "", isOpener: false, isBlank: true, token: name },
      );
    } else if (Object.hasOwn(values, name)) {
      plain += values[name];
    } else {
      plain += match[0];
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
