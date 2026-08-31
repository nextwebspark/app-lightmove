import { cleanText } from "./extractedCompany";

/**
 * What reading a page yields — a person as the page described them, before anyone edits it.
 *
 * Every field is optional and every field is a guess, the same premise `ExtractedCompany` has: the
 * popup renders each one as an editable input rather than writing it through.
 */
export interface ExtractedPerson {
  fullName: string | null;
  title: string | null;
  employerName: string | null;
  location: string | null;
  /** How long they have been in the current role, as the page words it — "Mar 2021 – Present · 4 yrs". */
  tenure: string | null;
  linkedinUrl: string | null;
  email: string | null;
  phone: string | null;
  /** Roles before the current one, most recent first. */
  career: ExtractedCareerEntry[];
}

export interface ExtractedCareerEntry {
  company: string | null;
  title: string | null;
  /** Free text, because that is what a page gives: "2016 – 2021", "c. 2015", "3 yrs 2 mos". */
  period: string | null;
}

/** A reader of one kind of page. Pure, so it can be tested against a saved fixture with no browser. */
export type PersonExtractor = (document: Document) => Partial<ExtractedPerson>;

export const EMPTY_EXTRACTED_PERSON: ExtractedPerson = {
  fullName: null,
  title: null,
  employerName: null,
  location: null,
  tenure: null,
  linkedinUrl: null,
  email: null,
  phone: null,
  career: [],
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
    title: firstAnswer(results, "title"),
    employerName: firstAnswer(results, "employerName"),
    location: firstAnswer(results, "location"),
    tenure: firstAnswer(results, "tenure"),
    linkedinUrl: firstAnswer(results, "linkedinUrl"),
    email: firstAnswer(results, "email"),
    phone: firstAnswer(results, "phone"),
    // Not firstAnswer: an empty array is truthy, so a reader that found no roles would win the field
    // outright and lock out the one that did.
    career: results.map((result) => result.career).find((career) => career?.length) ?? [],
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

/** An email out of prose or a mailto href, or nothing. */
export function emailOrNull(value: string | null | undefined): string | null {
  const text = cleanText(value)?.replace(/^mailto:/i, "").split("?")[0];
  if (!text) {
    return null;
  }
  const match = text.match(/[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+/);
  return match ? match[0] : null;
}
