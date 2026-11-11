import {
  SIGNATURE_NOT_IN_WORDS,
  templateTokenOptions,
  tokenNameOf,
  tokenUsesIn,
} from "../../outreach/lib/sequenceTokens";

/** The variables a template may hold here: the sequence bar's, less the opener. */
export function templateVocabulary(bookingLinkOffered: boolean): string[] {
  return templateTokenOptions(bookingLinkOffered).map(tokenNameOf);
}

/**
 * What is wrong with the variables in `text`, in the server's words, or null: a variable no email can fill would reach
 * the executive as typed, so a template never keeps one.
 */
export function variableProblemOf(text: string, vocabulary: readonly string[]): string | null {
  for (const use of tokenUsesIn(text)) {
    if (use.name === "signature") return SIGNATURE_NOT_IN_WORDS;
    if (use.name === "opener") return "{{opener}} is written for each person in a sequence, so a template cannot use it.";
    if (!vocabulary.includes(use.name)) {
      return `{{${use.name}}} isn't a variable Uncava can fill. Insert one from the Insert bar.`;
    }
    if (use.name === "bookingLink" && use.fallback !== null) return "A booking link cannot have a fallback.";
  }
  return null;
}
