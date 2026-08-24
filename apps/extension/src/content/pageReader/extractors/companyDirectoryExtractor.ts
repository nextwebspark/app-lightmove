import { cleanText, parseHeadcount, type CompanyExtractor, type ExtractedCompany } from "../extractedCompany";

/**
 * Company directories — Apollo and Crunchbase.
 *
 * Both render a company as a labelled fact table, and both change their markup often, so this reads
 * the *labels* and not the layout: find the element whose text is "Industry", take the value beside
 * it. That survives a restyle, which a class-name selector does not.
 *
 * Narrower than the structured-data reader on purpose — it runs only on the hosts below, because
 * "find the text next to the word Industry" is a guess that is reliable on a fact table and reckless
 * on a page of prose.
 */
export const companyDirectoryExtractor: CompanyExtractor = (document) => {
  if (!isDirectoryPage(document)) {
    return {};
  }
  const facts = readLabelledFacts(document);

  const extracted: Partial<ExtractedCompany> = {
    companyName: cleanText(document.querySelector("main h1, h1")?.textContent),
    website: facts.get("website"),
    linkedinUrl: linkedInLink(document),
    industry: facts.get("industry") ?? facts.get("industries"),
    numEmployees: parseHeadcount(facts.get("employees") ?? facts.get("company size") ?? facts.get("headcount")),
    companyCity: cityFrom(facts),
    companyCountry: countryFrom(facts),
    description: cleanText(facts.get("description") ?? facts.get("about")),
  };

  return Object.fromEntries(
    Object.entries(extracted).filter(([, value]) => value !== null && value !== undefined && value !== ""),
  ) as Partial<ExtractedCompany>;
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
  for (const candidate of document.querySelectorAll("main span, main div > span, li")) {
    const label = cleanText(candidate.textContent);
    if (label && label.length <= 20 && candidate.nextElementSibling) {
      remember(label, candidate.nextElementSibling.textContent);
    }
  }
  return facts;
}

function linkedInLink(document: Document): string | null {
  return document.querySelector('a[href*="linkedin.com/company/"]')?.getAttribute("href") ?? null;
}

function cityFrom(facts: Map<string, string>): string | null {
  const explicit = facts.get("city");
  if (explicit) {
    return explicit;
  }
  return splitPlace(facts.get("headquarters") ?? facts.get("location"))[0] ?? null;
}

function countryFrom(facts: Map<string, string>): string | null {
  const explicit = facts.get("country");
  if (explicit) {
    return explicit;
  }
  const segments = splitPlace(facts.get("headquarters") ?? facts.get("location"));
  return segments.length > 1 ? segments[segments.length - 1] : null;
}

function splitPlace(place: string | null | undefined): string[] {
  return cleanText(place)?.split(",").map((part) => part.trim()).filter(Boolean) ?? [];
}
