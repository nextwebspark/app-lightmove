import type { RecipientTokens } from "../api/sequenceApi";
import { BOOKING_LINK_PLACEHOLDER, TOKEN_PATTERN, tokenValueOf } from "./sequenceTokens";

/** What a consultant reads for each variable the composer fills, in the glossary's words. */
export const VARIABLE_LABELS: Record<string, string> = {
  firstName: "First name",
  currentTitle: "Current title",
  currentCompany: "Current company",
  positionTitle: "Position title",
  location: "Location",
  senderFirstName: "Your first name",
  bookingLink: "Your booking link",
};

export type VariableChipKind = "value" | "fallback" | "blank";

/** One variable as an email to one person shows it: their value where it falls, never `{{name}}`. */
export interface VariableChip {
  kind: VariableChipKind;
  /** The value, or the fallback standing in, or the variable's label where nothing is on file and nothing stands in. */
  text: string;
  label: string;
  title: string;
}

/** Null for a name no email fills: it stays as typed, so a misspelling shows. */
export function chipOf(
  name: string,
  fallback: string | null,
  tokens: RecipientTokens,
  forWhom: string,
): VariableChip | null {
  const label = VARIABLE_LABELS[name];
  if (!label) return null;
  if (name === "bookingLink") {
    const link = tokens.bookingLink?.trim() ?? "";
    return { kind: "value", text: link || BOOKING_LINK_PLACEHOLDER, label, title: `${label}: made for you when it is sent` };
  }
  const value = tokenValueOf(tokens, name);
  if (value === null) return null;
  if (value) {
    return { kind: "value", text: value, label, title: name === "senderFirstName" ? label : `${label}, from ${forWhom}'s profile` };
  }
  if (fallback) {
    return { kind: "fallback", text: fallback, label, title: `Nothing on file for ${forWhom}: the fallback stands in for ${label.toLowerCase()}` };
  }
  return { kind: "blank", text: label, label, title: `Nothing on file for ${forWhom}: ${label.toLowerCase()}` };
}

export const CHIP_CLASS: Record<VariableChipKind, string> = {
  value: "cursor-pointer rounded-u-control bg-u-accent-tint px-1 text-u-text",
  fallback: "cursor-pointer rounded-u-control border border-dashed border-u-border-strong px-1 text-u-text2",
  blank: "cursor-pointer rounded-u-control bg-u-offlimits-tint px-1 font-medium text-u-offlimits ring-1 ring-u-offlimits",
};

export type TextPiece = { text: string } | { text: string; name: string; chip: VariableChip };

/** A line of words cut into plain text and the chips its variables are, for a field that cannot draw them in place. */
export function piecesOf(text: string, tokens: RecipientTokens, forWhom: string): TextPiece[] {
  const pieces: TextPiece[] = [];
  let last = 0;
  for (const match of text.matchAll(new RegExp(TOKEN_PATTERN.source, "g"))) {
    const fallback = match[2]?.replace(/\s+/g, " ").trim() || null;
    const chip = chipOf(match[1], fallback, tokens, forWhom);
    if (!chip) continue;
    const at = match.index ?? 0;
    if (at > last) pieces.push({ text: text.slice(last, at) });
    pieces.push({ text: chip.text, name: match[1], chip });
    last = at + match[0].length;
  }
  if (last < text.length) pieces.push({ text: text.slice(last) });
  return pieces;
}
