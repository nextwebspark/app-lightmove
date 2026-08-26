import {
  cityOf,
  cleanText,
  countryOf,
  parseHeadcount,
  withoutEmpty,
  type CompanyExtractor,
  type ExtractedCompany,
} from "../extractedCompany";

/**
 * `linkedin.com/company/*` — the richest source, and the most fragile.
 *
 * LinkedIn's class names are generated and change without notice, so nothing here matches on one. Each
 * field is a chain of increasingly generic strategies, ending in one that reads the page's own
 * structured data or its visible definition list. When a strategy stops matching, the field comes back
 * null and the merge simply falls through to the next extractor — the capture still works with fewer
 * fields, which is the failure mode worth designing for.
 *
 * The fixture in `__fixtures__` is what these selectors were written against. When LinkedIn changes
 * and a chain needs extending, add a fixture rather than replacing the old one: a strategy that still
 * works for some users must not be deleted because a newer layout appeared.
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

function isLinkedInCompanyPage(document: Document): boolean {
  const url = canonicalCompanyUrl(document) ?? document.location?.href ?? "";
  return /linkedin\.com\/company\//i.test(url);
}

function canonicalCompanyUrl(document: Document): string | null {
  const canonical = document.querySelector('link[rel="canonical"]')?.getAttribute("href");
  if (canonical && /linkedin\.com\/company\//i.test(canonical)) {
    return canonical;
  }
  const ogUrl = document.querySelector('meta[property="og:url"]')?.getAttribute("content");
  return ogUrl && /linkedin\.com\/company\//i.test(ogUrl) ? ogUrl : null;
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
 * LinkedIn wraps outbound links in its own interstitial —
 * `linkedin.com/redir/redirect?url=https%3A%2F%2Falrawabidairy%2Eae&urlhash=…` — so the raw `href` is a
 * linkedin.com URL. Writing that through would file every company captured from LinkedIn under a
 * linkedin.com website, which is exactly what `isAggregatorHost` in `readCompanyFromPage` exists to
 * prevent; that guard only covers the address-bar fallback, so an extracted value routes around it.
 *
 * A LinkedIn URL with no `url` parameter yields nothing rather than itself, so the merge falls through
 * to the structured-data reader instead of recording a wrong answer. The visible link text is
 * deliberately not a fallback: LinkedIn truncates it ("https://www.alrawabidair…"), and a truncated
 * domain is a different company.
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
    return absolute;
  }
  const wrapped = parsed.searchParams.get("url");
  return wrapped ? wrapped : null;
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

function textOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.textContent ?? null;
}

function hrefOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.getAttribute("href") ?? null;
}
