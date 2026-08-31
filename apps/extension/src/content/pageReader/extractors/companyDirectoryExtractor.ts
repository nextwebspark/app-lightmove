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

/**
 * Company directories — Apollo and Crunchbase. Reads the *labels* rather than the layout, which
 * survives a restyle, and runs only on the hosts below: "the text beside the word Industry" is
 * reliable on a fact table and reckless on a page of prose.
 */
export const companyDirectoryExtractor: CompanyExtractor = (document) => {
  if (!isDirectoryPage(document)) {
    return {};
  }
  const facts = readLabelledFacts(document);

  const extracted: Partial<ExtractedCompany> = {
    companyName: cleanText(document.querySelector("main h1, h1")?.textContent),
    website: httpUrlOrNull(facts.get("website")),
    linkedinUrl: linkedInLink(document),
    industry: facts.get("industry") ?? facts.get("industries"),
    numEmployees: parseHeadcount(facts.get("employees") ?? facts.get("company size") ?? facts.get("headcount")),
    companyCity: cityFrom(facts),
    companyCountry: countryFrom(facts),
    description: cleanText(facts.get("description") ?? facts.get("about")),
  };

  return withoutEmpty(extracted);
};

const DIRECTORY_HOSTS = [/(^|\.)apollo\.io$/i, /(^|\.)crunchbase\.com$/i];

/** The labels worth reading. Anything else on the page is ignored rather than guessed at. */
const FACT_LABELS = new Set([
  "website",
  "industry",
  "industries",
  "employees",
  "company size",
  "headcount",
  "headquarters",
  "location",
  "city",
  "country",
  "description",
  "about",
]);

function isDirectoryPage(document: Document): boolean {
  const host = document.location?.hostname ?? "";
  return DIRECTORY_HOSTS.some((pattern) => pattern.test(host));
}

/**
 * Every "label: value" pair the page shows, whether it built them as a definition list, a table, or
 * two divs side by side. The label carries the meaning; the element that holds it does not.
 */
function readLabelledFacts(document: Document): Map<string, string> {
  const facts = new Map<string, string>();

  const remember = (rawLabel: string | null | undefined, rawValue: string | null | undefined) => {
    const label = cleanText(rawLabel)?.toLowerCase().replace(/:$/, "");
    const value = cleanText(rawValue);
    if (label && value && FACT_LABELS.has(label) && !facts.has(label)) {
      facts.set(label, value);
    }
  };

  for (const term of document.querySelectorAll("dt")) {
    remember(term.textContent, term.nextElementSibling?.textContent);
  }
  for (const row of document.querySelectorAll("tr")) {
    const cells = row.querySelectorAll("th, td");
    if (cells.length >= 2) {
      remember(cells[0].textContent, cells[1].textContent);
    }
  }
  // The div-pair layout: a short label element with exactly one sibling holding the value. The length
  // guard is what keeps a paragraph that happens to begin with "Industry" out of the map.
  // Order matters for cost, not behaviour. `textContent` serialises a whole subtree, and the selector
  // matches nested spans, so reading it first walked an outer span's text again for every descendant
  // that also matched — quadratic in nesting depth, synchronously, while the popup waits. A label is
  // a leaf with a sibling, so both of those are settled before anything is serialised.
  for (const candidate of document.querySelectorAll("main span, main div > span, li")) {
    if (candidate.children.length > 0 || !candidate.nextElementSibling) {
      continue;
    }
    const label = cleanText(candidate.textContent);
    if (label && label.length <= 20) {
      remember(label, candidate.nextElementSibling.textContent);
    }
  }
  return facts;
}

function linkedInLink(document: Document): string | null {
  for (const anchor of document.querySelectorAll('a[href*="linkedin.com/company/"]')) {
    const url = httpUrlOrNull(anchor.getAttribute("href"), document.location?.href);
    if (url && isLinkedInCompanyUrl(url)) {
      return url;
    }
  }
  return null;
}

/** An explicit city field wins; otherwise the first part of whatever place the page names. */
function cityFrom(facts: Map<string, string>): string | null {
  return facts.get("city") ?? cityOf(facts.get("headquarters") ?? facts.get("location"));
}

function countryFrom(facts: Map<string, string>): string | null {
  return facts.get("country") ?? countryOf(facts.get("headquarters") ?? facts.get("location"));
}
