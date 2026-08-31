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

/**
 * Drops the fields an extractor had nothing to say about.
 *
 * Load-bearing for the merge, not tidiness: an explicit null still counts as "this extractor answered"
 * and would stop a later, broader extractor filling the gap.
 */
export function withoutEmpty<T extends object>(fields: Partial<T>): Partial<T> {
  return Object.fromEntries(
    Object.entries(fields).filter(([, value]) => value !== null && value !== undefined && value !== ""),
  ) as Partial<T>;
}

/**
 * Splits a place into its parts — "Dubai, Dubai, United Arab Emirates" is city first, country last,
 * with whatever administrative region sits between them dropped.
 */
export function splitPlaceParts(place: string | null | undefined): string[] {
  return cleanText(place)?.split(",").map((part) => part.trim()).filter(Boolean) ?? [];
}

/** The city a place names, or nothing. */
export function cityOf(place: string | null | undefined): string | null {
  return splitPlaceParts(place)[0] ?? null;
}

/** The country a place names — the last part, and only when there is more than one. */
export function countryOf(place: string | null | undefined): string | null {
  const parts = splitPlaceParts(place);
  return parts.length > 1 ? parts[parts.length - 1] : null;
}

/**
 * A page-supplied URL, or nothing unless it is http(s).
 *
 * Every URL an extractor yields is text a hostile page controls, and it is persisted rather than
 * merely displayed — so a `javascript:` href planted today detonates on whichever future screen
 * turns a stored website into a link. Checking the parsed protocol here, once, is what stops that
 * being a decision every render site has to remember to make.
 */
export function httpUrlOrNull(value: string | null | undefined, base?: string): string | null {
  const candidate = cleanText(value);
  if (!candidate) {
    return null;
  }
  // Parsing is the check, never a rewrite: the caller's own text comes back, because `URL.href`
  // normalises ("example.ae" gains a trailing slash) and would churn every value already stored.
  if (isAbsolute(candidate)) {
    return isHttp(parseOrNull(candidate)) ? candidate : null;
  }
  // A bare domain — "zenith-industrial.sa" — is what a directory page's "Website" row usually holds.
  if (hasHostShape(candidate)) {
    return candidate;
  }
  // A relative href is meaningless alone, so this is the one case resolved against its page.
  const resolved = base ? parseOrNull(candidate, base) : null;
  return isHttp(resolved) ? resolved!.href : null;
}

function parseOrNull(value: string, base?: string): URL | null {
  try {
    return new URL(value, base);
  } catch {
    return null;
  }
}

function isHttp(url: URL | null): boolean {
  return url !== null && (url.protocol === "https:" || url.protocol === "http:");
}

/** A scheme-less host, "acme.ae" or "acme.ae/about" — never a path, and never a scheme in disguise. */
function hasHostShape(value: string): boolean {
  const host = parseOrNull(`https://${value}`)?.hostname;
  return host !== undefined && host.includes(".") && value.startsWith(host);
}

function isAbsolute(value: string): boolean {
  return /^[a-z][a-z0-9+.-]*:/i.test(value) || value.startsWith("//");
}

/** Whether a URL is a LinkedIn company page, by parsed host — never a substring of the whole URL. */
export function isLinkedInCompanyUrl(value: string | null | undefined): boolean {
  if (!value) {
    return false;
  }
  try {
    const url = new URL(value);
    return /(^|\.)linkedin\.com$/i.test(url.hostname) && url.pathname.startsWith("/company/");
  } catch {
    return false;
  }
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
