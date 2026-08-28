import {
  cleanText,
  httpUrlOrNull,
  isLinkedInCompanyUrl,
  mergeExtracted,
  parseHeadcount,
  withoutEmpty,
  type CompanyExtractor,
  type ExtractedCompany,
} from "../extractedCompany";

/**
 * The universal reader: schema.org JSON-LD, then OpenGraph, then the page's own head.
 *
 * The one that matters most, even though it yields the fewest fields. LightMove's market is the GCC,
 * where the long tail of companies has a plain corporate site and no directory entry at all — those
 * pages are exactly the ones a per-site extractor cannot be written for. Almost all of them publish
 * *something* structured, because search engines reward it.
 *
 * Ordered inside the file too: JSON-LD is a publisher's deliberate description of itself and beats
 * OpenGraph, which is written for a social card and often says the page rather than the company.
 */
export const structuredDataExtractor: CompanyExtractor = (document) => ({
  ...fromOpenGraph(document),
  ...fromJsonLd(document),
});

interface OrganizationJsonLd {
  "@type"?: unknown;
  name?: unknown;
  legalName?: unknown;
  url?: unknown;
  sameAs?: unknown;
  description?: unknown;
  numberOfEmployees?: unknown;
  address?: unknown;
}

const ORGANIZATION_TYPES = new Set([
  "Organization",
  "Corporation",
  "LocalBusiness",
  "OnlineBusiness",
  "NGO",
  "GovernmentOrganization",
  "EducationalOrganization",
]);

/**
 * Every Organization node, merged in document order rather than only the first.
 *
 * A page commonly carries a publisher or `WebSite` stub — name and logo, nothing else — ahead of the
 * real company node. Taking `[0]` would answer `companyName` from the stub, and because the merge is
 * first-non-empty that one bad early answer would beat every good later one.
 */
function fromJsonLd(document: Document): Partial<ExtractedCompany> {
  return withoutEmpty(mergeExtracted(findOrganizationNodes(document).map(toCompany)));
}

function toCompany(node: OrganizationJsonLd): Partial<ExtractedCompany> {
  const address = asRecord(node.address);
  return withoutEmpty({
    companyName: cleanText(asText(node.legalName) ?? asText(node.name)),
    website: httpUrlOrNull(asText(node.url)),
    linkedinUrl: linkedInFrom(node.sameAs),
    description: cleanText(asText(node.description)),
    numEmployees: parseHeadcount(headcountFrom(node.numberOfEmployees)),
    companyCity: cleanText(asText(address?.["addressLocality"])),
    companyCountry: cleanText(asText(address?.["addressCountry"])),
  });
}

/**
 * Walks every JSON-LD block, including the `@graph` array pages commonly wrap everything in — an
 * Organization is very often the second or third node there, behind a WebSite and a WebPage.
 */
function findOrganizationNodes(document: Document): OrganizationJsonLd[] {
  const found: OrganizationJsonLd[] = [];
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(script.textContent ?? "");
    } catch {
      // A malformed block on one page must not stop the others being read.
      continue;
    }
    for (const node of flattenGraph(parsed)) {
      if (isOrganization(node)) {
        found.push(node);
      }
    }
  }
  return found;
}

function flattenGraph(value: unknown, depth = 0): OrganizationJsonLd[] {
  if (depth > 4 || value === null || typeof value !== "object") {
    return [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((entry) => flattenGraph(entry, depth + 1));
  }
  const node = value as OrganizationJsonLd & { "@graph"?: unknown };
  return [node, ...flattenGraph(node["@graph"], depth + 1)];
}

function isOrganization(node: OrganizationJsonLd): boolean {
  const type = node["@type"];
  if (typeof type === "string") {
    return ORGANIZATION_TYPES.has(type);
  }
  return Array.isArray(type) && type.some((entry) => typeof entry === "string" && ORGANIZATION_TYPES.has(entry));
}

function fromOpenGraph(document: Document): Partial<ExtractedCompany> {
  return withoutEmpty({
    companyName: cleanText(metaContent(document, "og:site_name")),
    website: httpUrlOrNull(metaContent(document, "og:url") ?? canonicalHref(document)),
    description: cleanText(metaContent(document, "og:description") ?? metaContent(document, "description")),
  });
}

function metaContent(document: Document, name: string): string | null {
  const tag = document.querySelector(`meta[property="${name}"], meta[name="${name}"]`);
  return tag?.getAttribute("content") ?? null;
}

function canonicalHref(document: Document): string | null {
  return document.querySelector('link[rel="canonical"]')?.getAttribute("href") ?? null;
}

/** `sameAs` is where a site lists its social profiles, and where its LinkedIn page usually is. */
function linkedInFrom(sameAs: unknown): string | null {
  const candidates = Array.isArray(sameAs) ? sameAs : [sameAs];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(asText(candidate));
    if (url && isLinkedInCompanyUrl(url)) {
      return url;
    }
  }
  return null;
}

/** schema.org allows a bare number, a string, or a QuantitativeValue with `value` or `minValue`. */
function headcountFrom(value: unknown): string | null {
  if (typeof value === "number") {
    return String(value);
  }
  const text = asText(value);
  if (text) {
    return text;
  }
  const record = asRecord(value);
  return record ? (asText(record["minValue"]) ?? asText(record["value"])) : null;
}

function asText(value: unknown): string | null {
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number") {
    return String(value);
  }
  // schema.org lets almost anything be an object with a `name`, e.g. addressCountry as a Country.
  const record = asRecord(value);
  return record && typeof record["name"] === "string" ? record["name"] : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}
