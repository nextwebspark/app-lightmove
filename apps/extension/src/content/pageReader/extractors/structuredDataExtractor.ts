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
import { asRecord, asText, canonicalHref, metaContent, nodesOfType, type JsonLdNode } from "./jsonLd";

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

const ORGANIZATION_TYPES = new Set([
  "Organization",
  "Corporation",
  "LocalBusiness",
  "OnlineBusiness",
  "NGO",
  "GovernmentOrganization",
  "EducationalOrganization",
]);

function fromJsonLd(document: Document): Partial<ExtractedCompany> {
  return withoutEmpty(mergeExtracted(nodesOfType(document, ORGANIZATION_TYPES).map(toCompany)));
}

function toCompany(node: JsonLdNode): Partial<ExtractedCompany> {
  const address = asRecord(node["address"]);
  return withoutEmpty({
    companyName: cleanText(asText(node["legalName"]) ?? asText(node["name"])),
    website: httpUrlOrNull(asText(node["url"])),
    linkedinUrl: linkedInFrom(node["sameAs"]),
    description: cleanText(asText(node["description"])),
    numEmployees: parseHeadcount(headcountFrom(node["numberOfEmployees"])),
    companyCity: cleanText(asText(address?.["addressLocality"])),
    companyCountry: cleanText(asText(address?.["addressCountry"])),
  });
}

function fromOpenGraph(document: Document): Partial<ExtractedCompany> {
  return withoutEmpty({
    companyName: cleanText(metaContent(document, "og:site_name")),
    website: httpUrlOrNull(metaContent(document, "og:url") ?? canonicalHref(document)),
    description: cleanText(metaContent(document, "og:description") ?? metaContent(document, "description")),
  });
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
