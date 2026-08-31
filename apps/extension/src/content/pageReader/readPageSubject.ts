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
 * URL shape first, because it is the one signal the page cannot lie about; extracted signal only where
 * the URL says nothing.
 *
 * A name alone is never enough — `og:title` on any article yields a name-shaped string — so a person
 * has to come with a title, an employer or a career. Person wins the tie: on a corporate bio page both
 * are present, and the consultant standing there is looking at the person.
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
