/**
 * The `@Size` caps the two capture DTOs carry, keyed by wire field name.
 *
 * Mirrored because the URL fields are never rendered — sent silently with the save — so an over-long
 * one from a page would be a 400 about a field the consultant can neither see nor clear.
 */
export const FIELD_LIMITS = {
  companyName: 200,
  companyLinkedinUrl: 500,
  sourceUrl: 1000,
  note: 2000,

  fullName: 200,
  linkedinUrl: 500,
} as const;

/** The longest the server will take, with an ellipsis where something was dropped. */
export function cappedAt(value: string | null | undefined, limit: number): string | null {
  const text = value?.trim();
  if (!text) {
    return null;
  }
  return text.length <= limit ? text : `${text.slice(0, limit - 1)}…`;
}
