/**
 * The `@Size` caps the two capture DTOs carry, keyed by wire field name.
 *
 * Mirrored because several of these fields are never rendered — the description, the source URL, a
 * career entry's period — so an over-long one from a page is a 400 about a field the consultant can
 * neither see nor clear.
 */
export const FIELD_LIMITS = {
  companyName: 200,
  industry: 200,
  companyCity: 100,
  companyCountry: 100,
  website: 500,
  companyLinkedinUrl: 500,
  shortDescription: 2000,
  note: 2000,
  sourceUrl: 1000,

  fullName: 200,
  title: 200,
  employerName: 200,
  email: 320,
  phone: 50,
  linkedinUrl: 500,
  locationCity: 100,
  locationCountry: 100,
  careerCompany: 200,
  careerTitle: 200,
  careerPeriod: 60,
} as const;

/** The longest the server will take, with an ellipsis where something was dropped. */
export function cappedAt(value: string | null | undefined, limit: number): string | null {
  const text = value?.trim();
  if (!text) {
    return null;
  }
  return text.length <= limit ? text : `${text.slice(0, limit - 1)}…`;
}

/** Career entries are capped in count as well as in length; the DTO takes 25. */
export const MAX_CAREER_ENTRIES = 25;
