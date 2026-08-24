/**
 * What reading a page yields — a company as the page described it, before anyone edits it.
 *
 * Every field is optional and every field is a guess. That is why the popup renders each one as an
 * editable input rather than writing them straight through: an extractor reading a corporate "About"
 * page is doing pattern-matching on prose, and it should be easy for a consultant to correct it.
 *
 * The shape every extractor returns a `Partial` of, which is what lets them be merged field by field:
 * the LinkedIn reader may know the headcount while the structured-data reader knows the legal name.
 */
export interface ExtractedCompany {
  companyName: string | null;
  website: string | null;
  linkedinUrl: string | null;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  description: string | null;
}

/** A reader of one kind of page. Pure, so it can be tested against a saved fixture with no browser. */
export type CompanyExtractor = (document: Document) => Partial<ExtractedCompany>;

export const EMPTY_EXTRACTED_COMPANY: ExtractedCompany = {
  companyName: null,
  website: null,
  linkedinUrl: null,
  industry: null,
  companyCountry: null,
  companyCity: null,
  numEmployees: null,
  description: null,
};

/**
 * Folds extractor results together, first non-empty value per field.
 *
 * Order is the priority: the caller passes the most specific reader first. A later extractor can fill
 * a field an earlier one left blank but can never overwrite one it answered, so adding a broad
 * fallback reader cannot degrade a page a specific reader already understood.
 *
 * Written out field by field rather than looped over `Object.entries`, which would need a cast to
 * assign back into a heterogeneous record. This way adding a field to `ExtractedCompany` fails the
 * build here until it is merged too, instead of silently arriving as null on every page.
 */
export function mergeExtracted(results: Partial<ExtractedCompany>[]): ExtractedCompany {
  return {
    companyName: firstAnswer(results, "companyName"),
    website: firstAnswer(results, "website"),
    linkedinUrl: firstAnswer(results, "linkedinUrl"),
    industry: firstAnswer(results, "industry"),
    companyCountry: firstAnswer(results, "companyCountry"),
    companyCity: firstAnswer(results, "companyCity"),
    numEmployees: firstAnswer(results, "numEmployees"),
    description: firstAnswer(results, "description"),
  };
}

/** The first extractor with something to say about this field, or nothing if none had. */
function firstAnswer<K extends keyof ExtractedCompany>(
  results: Partial<ExtractedCompany>[],
  field: K,
): ExtractedCompany[K] {
  for (const result of results) {
    const value = result[field];
    if (value !== null && value !== undefined && value !== "") {
      return value;
    }
  }
  return EMPTY_EXTRACTED_COMPANY[field];
}

/** Collapses whitespace and trims; returns null for what is left of an empty string. */
export function cleanText(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const collapsed = value.replace(/\s+/g, " ").trim();
  return collapsed.length > 0 ? collapsed : null;
}

/**
 * A headcount out of prose — "1,001-5,000 employees" is 1001, "51-200" is 51.
 *
 * The low end of a range on purpose: the API stores a single figure, and a mandate filtering on
 * "at least 1,000 people" should not have a company let in by the top of a band it barely reaches.
 */
export function parseHeadcount(value: string | null | undefined): number | null {
  const text = cleanText(value);
  if (!text) {
    return null;
  }
  const firstNumber = text.replace(/,/g, "").match(/\d+/);
  if (!firstNumber) {
    return null;
  }
  const parsed = Number.parseInt(firstNumber[0], 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
