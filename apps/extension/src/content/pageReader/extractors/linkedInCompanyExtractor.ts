import {
  cleanText,
  httpUrlOrNull,
  isLinkedInCompanyUrl,
  withoutEmpty,
  type CompanyExtractor,
  type ExtractedCompany,
} from "../extractedCompany";
import { textOf } from "./dom";

/**
 * `linkedin.com/company/*` — the company's name, and nothing more. Class names are generated and
 * churn per deploy, so the chain ends at the tab title, which every layout still carries.
 */
export const linkedInCompanyExtractor: CompanyExtractor = (document) => {
  if (!isLinkedInCompanyPage(document)) {
    return {};
  }
  const extracted: Partial<ExtractedCompany> = {
    // Scoped like the profile's: a bare `h1` reaches LinkedIn's own chrome, which names the viewer.
    companyName: cleanText(
      textOf(document, 'h1[class*="org-top-card"]')
        ?? textOf(document, "main h1"),
    ) ?? nameFromDocumentTitle(document),
    linkedinUrl: canonicalCompanyUrl(document),
  };
  return withoutEmpty(extracted);
};

/** "Al Rawabi Dairy Company | LinkedIn", with the notification count and the suffix dropped. */
function nameFromDocumentTitle(document: Document): string | null {
  const titleText = cleanText(document.title)
    ?.replace(/^\(\d+\+?\)\s*/, "")
    .replace(/\s*[|│]\s*LinkedIn\s*$/i, "")
    // The company tab title carries a section suffix: "Acme: About", "Acme: Jobs".
    .replace(/:\s*(about|jobs|people|posts|life|overview)\s*$/i, "");
  const name = cleanText(titleText);
  // LinkedIn rewrites the title mid-navigation to its own section names; see the profile extractor.
  return name && !/^(linkedin|feed|home|messaging|notifications|my network|jobs|search)$/i.test(name)
    ? name
    : null;
}

/**
 * Keyed on the host the browser actually loaded, never on `canonical`/`og:url` — those are page-supplied,
 * so any site could otherwise declare itself a LinkedIn company page and be read by this extractor.
 */
function isLinkedInCompanyPage(document: Document): boolean {
  const hostname = document.location?.hostname ?? "";
  return /(^|\.)linkedin\.com$/i.test(hostname) && (document.location?.pathname ?? "").startsWith("/company/");
}

function canonicalCompanyUrl(document: Document): string | null {
  const candidates = [
    document.querySelector('link[rel="canonical"]')?.getAttribute("href"),
    document.querySelector('meta[property="og:url"]')?.getAttribute("content"),
    document.location?.href,
  ];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(candidate);
    if (url && isLinkedInCompanyUrl(url)) {
      return url;
    }
  }
  return null;
}
