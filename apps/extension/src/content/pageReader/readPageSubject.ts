import type { ExtractedCompany } from "./extractedCompany";
import type { ExtractedPerson } from "./extractedPerson";
import { readCompanyFromPage } from "./readCompanyFromPage";
import { readPersonFromPage } from "./readPersonFromPage";

export type PageSubjectKind = "person" | "company" | "unknown";

export interface PageSubject {
  subject: PageSubjectKind;
  person: ExtractedPerson;
  company: ExtractedCompany;
}

/**
 * The one thing injected into a page: read both sides, and say which the consultant is looking at.
 *
 * Both, from a single injection, because the two are not alternatives — a person needs their employer
 * and an executive bio carries the site's own Organization alongside the Person. Classifying here
 * rather than in the popup is also what lets the tab preselect itself.
 */
export function readPageSubject(document: Document): PageSubject {
  const person = readPersonFromPage(document);
  const company = readCompanyFromPage(document);
  return { subject: classify(document, person, company), person, company };
}

/**
 * URL shape first — the one signal a page cannot lie about — then extracted signal. A name alone is
 * never enough, since every article's `og:title` is name-shaped. Person wins the tie: a bio page
 * carries both, and the consultant is looking at the person.
 */
function classify(document: Document, person: ExtractedPerson, company: ExtractedCompany): PageSubjectKind {
  const pathname = document.location?.pathname ?? "";
  if (/(^|\.)linkedin\.com$/i.test(document.location?.hostname ?? "")) {
    if (pathname.startsWith("/in/")) {
      return "person";
    }
    if (pathname.startsWith("/company/")) {
      return "company";
    }
  }
  if (person.fullName && (person.title || person.employerName || person.career.length > 0)) {
    return "person";
  }
  return company.companyName ? "company" : "unknown";
}
