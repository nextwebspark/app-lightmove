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
import { asRecord, asText, canonicalHref, linkedInFrom, metaContent, nodesOfType, type JsonLdNode } from "./jsonLd";

/**
 * The universal reader: schema.org JSON-LD, then OpenGraph. Fewest fields and the most important —
 * the GCC long tail has a plain corporate site and no directory entry, and JSON-LD is all such a page
 * offers. JSON-LD beats OpenGraph, which describes the page rather than the company.
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
    linkedinUrl: linkedInFrom(node["sameAs"], isLinkedInCompanyUrl, httpUrlOrNull),
    description: cleanText(asText(node["description"])),
    numEmployees: parseHeadcount(headcountFrom(node["numberOfEmployees"])),
    companyCity: cleanText(asText(address?.["addressLocality"])),
    companyCountry: cleanText(asText(address?.["addressCountry"])),
  });
}

/**
 * `og:site_name` is not a claim to be a company — every publisher, blog and marketing page carries one,
 * and reading it as a name made `readPageSubject` answer "company" for the whole web, emptying out the
 * "unknown" branch that exists to leave the tab alone. It is offered only where the page also says it
 * is about an organisation.
 */
function fromOpenGraph(document: Document): Partial<ExtractedCompany> {
  const ogType = cleanText(metaContent(document, "og:type"))?.toLowerCase();
  const namesAnOrganisation = ogType === "business.business" || ogType === "company" || ogType === "profile";
  return withoutEmpty({
    companyName: namesAnOrganisation ? cleanText(metaContent(document, "og:site_name")) : null,
    website: httpUrlOrNull(metaContent(document, "og:url") ?? canonicalHref(document)),
    description: cleanText(metaContent(document, "og:description") ?? metaContent(document, "description")),
  });
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
