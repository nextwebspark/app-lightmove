/**
 * What reading a page yields — a company as the page described it, before anyone edits it.
 *
 * V1 captures what a LinkedIn page states reliably: the company's name and its LinkedIn page.
 * Everything richer (industry, headcount, location, website) is enrichment, done server-side
 * later. Every field is a guess, which is why the popup renders the name as an editable input, and
 * every extractor returns a `Partial` of this so the guesses merge field by field.
 */
export interface ExtractedCompany {
  companyName: string | null;
  linkedinUrl: string | null;
}

/** A reader of one kind of page. Pure, so it can be tested against a saved fixture with no browser. */
export type CompanyExtractor = (document: Document) => Partial<ExtractedCompany>;

const EMPTY_EXTRACTED_COMPANY: ExtractedCompany = {
  companyName: null,
  linkedinUrl: null,
};

/**
 * Folds extractor results together, first non-empty value per field, most specific reader first.
 *
 * Written out field by field on purpose: adding a field to `ExtractedCompany` fails the build here
 * until it is merged too, rather than silently arriving as null on every page.
 */
export function mergeExtracted(results: Partial<ExtractedCompany>[]): ExtractedCompany {
  return {
    companyName: firstAnswer(results, "companyName"),
    linkedinUrl: firstAnswer(results, "linkedinUrl"),
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
 * A page-supplied URL, or nothing unless it is http(s). Hostile text that gets *persisted*, so a
 * `javascript:` href planted today would detonate on whichever future screen renders it as a link.
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

/** A scheme-less host, "acme.ae" or "Acme.AE/about" — never a path, and never a scheme in disguise. */
function hasHostShape(value: string): boolean {
  // Compared lower-cased, because WHATWG lower-cases the host while parsing: a directory row reading
  // "Zenith-Industrial.sa" would otherwise fail its own host check and be dropped as not a URL.
  const host = parseOrNull(`https://${value}`)?.hostname;
  return host !== undefined && host.includes(".") && value.toLowerCase().startsWith(host);
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
