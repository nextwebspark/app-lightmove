import { httpUrlOrNull, mergeExtracted, type ExtractedCompany } from "./extractedCompany";
import { mergeExtractedPerson, type ExtractedPerson } from "./extractedPerson";
import { linkedInCompanyExtractor } from "./extractors/linkedInCompanyExtractor";
import { linkedInProfileExtractor } from "./extractors/linkedInProfileExtractor";

export type PageSubjectKind = "person" | "company" | "unknown";

export interface PageSubject {
  subject: PageSubjectKind;
  person: ExtractedPerson;
  company: ExtractedCompany;
  /** The address the page had while it was read, so the worker can refuse an answer about another. */
  pageUrl: string;
  /**
   * The address the page *declares* it is — `canonical`, else `og:url`, and never the address bar.
   *
   * Kept apart from `pageUrl` because the two disagree in exactly the case that matters: LinkedIn
   * navigates with pushState, so the address bar is the new profile while the head still declares the
   * previous one. Evidence of staleness only — a page-supplied value can make a read wait, never make
   * one trusted.
   */
  declaredUrl: string | null;
}

/**
 * The one thing injected into a page. LinkedIn only, and the URL is the whole classification —
 * `/in/` is a person, `/company/` is a company, and anything else (the feed, search, jobs) is
 * "unknown", which the worker answers with "open a profile or company page" rather than a guess.
 */
export function readPageSubject(document: Document): PageSubject {
  return {
    subject: classify(document),
    person: mergeExtractedPerson([linkedInProfileExtractor(document)]),
    company: mergeExtracted([linkedInCompanyExtractor(document)]),
    pageUrl: document.location?.href ?? "",
    declaredUrl: declaredUrlOf(document),
  };
}

function classify(document: Document): PageSubjectKind {
  const pathname = document.location?.pathname ?? "";
  if (!/(^|\.)linkedin\.com$/i.test(document.location?.hostname ?? "")) {
    return "unknown";
  }
  if (pathname.startsWith("/in/")) {
    return "person";
  }
  return pathname.startsWith("/company/") ? "company" : "unknown";
}

function declaredUrlOf(document: Document): string | null {
  const candidates = [
    document.querySelector('link[rel="canonical"]')?.getAttribute("href"),
    document.querySelector('meta[property="og:url"]')?.getAttribute("content"),
  ];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(candidate);
    if (url) {
      return url;
    }
  }
  return null;
}
