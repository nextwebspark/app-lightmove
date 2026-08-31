import {
  cityOf,
  cleanText,
  countryOf,
  httpUrlOrNull,
  isLinkedInCompanyUrl,
  parseHeadcount,
  withoutEmpty,
  type CompanyExtractor,
  type ExtractedCompany,
} from "../extractedCompany";
import { hrefOf, textOf } from "./dom";

/**
 * `linkedin.com/company/*` — the richest source, and the most fragile. Class names are generated, so
 * every field is a fallback chain and a broken one yields null rather than a wrong answer. Extend a
 * chain with a new fixture beside the old, never by replacing it.
 */
export const linkedInCompanyExtractor: CompanyExtractor = (document) => {
  if (!isLinkedInCompanyPage(document)) {
    return {};
  }
  const details = readDefinitionList(document);
  const detailLinks = readDefinitionLinks(document);

  const extracted: Partial<ExtractedCompany> = {
    companyName: cleanText(
      textOf(document, 'h1[class*="org-top-card"]')
        ?? textOf(document, "main h1")
        ?? textOf(document, "h1"),
    ),
    linkedinUrl: canonicalCompanyUrl(document),
    // The href before the text, and unwrapped before either: see companyWebsiteFrom.
    website: companyWebsiteFrom(detailLinks.get("website") ?? hrefOf(document, WEBSITE_LINK_SELECTOR)),
    industry: details.get("industry"),
    numEmployees: parseHeadcount(details.get("company size") ?? companySizeFromSummary(document)),
    companyCity: cityOf(details.get("headquarters")),
    companyCountry: countryOf(details.get("headquarters")),
    description: details.get("overview") ?? cleanText(textOf(document, 'p[class*="about-us__description"]')),
  };

  return withoutEmpty(extracted);
};

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

/**
 * The "About" panel, read as the definition list it is.
 *
 * LinkedIn has rendered this as `<dl><dt>Industry</dt><dd>…</dd></dl>` across every layout of the page
 * so far — the labels are the stable part, not the markup around them, so the term text is what this
 * keys on. Lower-cased, because the same label has shipped as "Company size" and "COMPANY SIZE".
 */
function readDefinitionList(document: Document): Map<string, string> {
  const details = new Map<string, string>();
  for (const list of document.querySelectorAll("dl")) {
    const terms = [...list.querySelectorAll("dt")];
    for (const term of terms) {
      const label = cleanText(term.textContent)?.toLowerCase().replace(/:$/, "");
      const value = cleanText(nextDefinition(term)?.textContent);
      if (label && value && !details.has(label)) {
        details.set(label, value);
      }
    }
  }
  return details;
}

const WEBSITE_LINK_SELECTOR = 'a[data-tracking-control-name*="website"]';

/**
 * The company's own site, out of whatever LinkedIn put in the link.
 *
 * LinkedIn wraps outbound links in `linkedin.com/redir/redirect?url=…`, so the raw href is a
 * linkedin.com URL — writing that through files every captured company under linkedin.com, routing
 * around `isAggregatorHost`. The visible text is not a fallback: LinkedIn truncates it, and a
 * truncated domain is a different company.
 */
function companyWebsiteFrom(href: string | null | undefined): string | null {
  if (!href) {
    return null;
  }
  const absolute = href.startsWith("//") ? `https:${href}` : href;
  let parsed: URL;
  try {
    parsed = new URL(absolute, "https://www.linkedin.com");
  } catch {
    return null;
  }
  if (!/(^|\.)linkedin\.com$/i.test(parsed.hostname)) {
    return httpUrlOrNull(absolute);
  }
  return httpUrlOrNull(parsed.searchParams.get("url"));
}

/** The href of the first link in each `<dd>`, for the labels whose value is a URL. */
function readDefinitionLinks(document: Document): Map<string, string> {
  const links = new Map<string, string>();
  for (const term of document.querySelectorAll("dt")) {
    const label = cleanText(term.textContent)?.toLowerCase().replace(/:$/, "");
    const href = nextDefinition(term)?.querySelector("a[href]")?.getAttribute("href");
    if (label && href && !links.has(label)) {
      links.set(label, href);
    }
  }
  return links;
}

/** The `<dd>` after a `<dt>`, skipping any element LinkedIn wraps between them. */
function nextDefinition(term: Element): Element | null {
  let sibling = term.nextElementSibling;
  while (sibling && sibling.tagName !== "DD") {
    if (sibling.tagName === "DT") {
      return null;
    }
    sibling = sibling.nextElementSibling;
  }
  return sibling;
}

/** The header sometimes carries "1,001-5,000 employees" where the About panel is not rendered. */
function companySizeFromSummary(document: Document): string | null {
  const summary = cleanText(document.querySelector('[class*="org-top-card-summary-info"]')?.textContent);
  return summary?.match(/[\d,]+(?:-[\d,]+)?\s+employees/i)?.[0] ?? null;
}
