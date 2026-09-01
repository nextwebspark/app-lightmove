import { mergeExtracted, type ExtractedCompany } from "./extractedCompany";
import { mergeExtractedPerson, type ExtractedPerson } from "./extractedPerson";
import { linkedInCompanyExtractor } from "./extractors/linkedInCompanyExtractor";
import { linkedInProfileExtractor } from "./extractors/linkedInProfileExtractor";

export type PageSubjectKind = "person" | "company" | "unknown";

export interface PageSubject {
  subject: PageSubjectKind;
  person: ExtractedPerson;
  company: ExtractedCompany;
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
