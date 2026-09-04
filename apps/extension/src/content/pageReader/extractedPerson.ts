/**
 * What reading a page yields — a person as the page described them, before anyone edits it.
 *
 * V1 captures the two fields a page states reliably: who the person is, and where their profile
 * lives. Everything richer (title, employer, career) is enrichment, done server-side later — the
 * page-side guesses proved too fragile to ship.
 */
export interface ExtractedPerson {
  fullName: string | null;
  linkedinUrl: string | null;
}

/** A reader of one kind of page. Pure, so it can be tested against a saved fixture with no browser. */
export type PersonExtractor = (document: Document) => Partial<ExtractedPerson>;

const EMPTY_EXTRACTED_PERSON: ExtractedPerson = {
  fullName: null,
  linkedinUrl: null,
};

/**
 * Folds extractor results together, first non-empty value per field, most specific extractor first.
 *
 * Written out field by field for the reason `mergeExtracted` is: adding a field fails the build here
 * until it is merged too, rather than silently arriving as null on every page.
 */
export function mergeExtractedPerson(results: Partial<ExtractedPerson>[]): ExtractedPerson {
  return {
    fullName: firstAnswer(results, "fullName"),
    linkedinUrl: firstAnswer(results, "linkedinUrl"),
  };
}

function firstAnswer<K extends keyof ExtractedPerson>(
  results: Partial<ExtractedPerson>[],
  field: K,
): ExtractedPerson[K] {
  for (const result of results) {
    const value = result[field];
    if (value !== null && value !== undefined && value !== "") {
      return value;
    }
  }
  return EMPTY_EXTRACTED_PERSON[field];
}

/** Whether a URL is a LinkedIn member profile, by parsed host — never a substring of the whole URL. */
export function isLinkedInProfileUrl(value: string | null | undefined): boolean {
  if (!value) {
    return false;
  }
  try {
    const url = new URL(value);
    return /(^|\.)linkedin\.com$/i.test(url.hostname) && url.pathname.startsWith("/in/");
  } catch {
    return false;
  }
}
