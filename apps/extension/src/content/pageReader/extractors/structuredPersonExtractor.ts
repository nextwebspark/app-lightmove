import { cleanText, httpUrlOrNull, withoutEmpty } from "../extractedCompany";
import {
  emailOrNull,
  isLinkedInProfileUrl,
  mergeExtractedPerson,
  type ExtractedPerson,
  type PersonExtractor,
} from "../extractedPerson";
import { asRecord, asText, metaContent, nodesOfType, type JsonLdNode } from "./jsonLd";

/**
 * The universal person reader: schema.org `Person`, then OpenGraph's profile tags.
 *
 * What makes a corporate leadership page readable at all — the GCC long tail publishes executive bios
 * on its own site and nowhere else, and those pages carry no markup a per-site extractor could target.
 */
export const structuredPersonExtractor: PersonExtractor = (document) => ({
  ...fromOpenGraph(document),
  ...fromJsonLd(document),
});

const PERSON_TYPES = new Set(["Person"]);

function fromJsonLd(document: Document): Partial<ExtractedPerson> {
  return withoutEmpty(mergeExtractedPerson(nodesOfType(document, PERSON_TYPES).map(toPerson)));
}

function toPerson(node: JsonLdNode): Partial<ExtractedPerson> {
  const address = asRecord(node["address"]);
  return withoutEmpty({
    fullName: cleanText(asText(node["name"]) ?? joinedName(node)),
    title: cleanText(asText(node["jobTitle"])),
    employerName: cleanText(asText(node["worksFor"])),
    location: placeOf(cleanText(asText(address?.["addressLocality"])),
      cleanText(asText(address?.["addressCountry"]))),
    linkedinUrl: linkedInFrom(node["sameAs"]),
    email: emailOrNull(asText(node["email"])),
    phone: cleanText(asText(node["telephone"])),
  });
}

function joinedName(node: JsonLdNode): string | null {
  const parts = [asText(node["givenName"]), asText(node["familyName"])].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : null;
}

/** The one string the popup splits back into city and country, so both readers speak the same shape. */
function placeOf(city: string | null, country: string | null): string | null {
  return [city, country].filter(Boolean).join(", ") || null;
}

/**
 * OpenGraph names a profile but never says whose page it is, so `og:title` is read only when
 * `og:type` claims a profile — otherwise every article headline would arrive as a person's name.
 */
function fromOpenGraph(document: Document): Partial<ExtractedPerson> {
  if (cleanText(metaContent(document, "og:type"))?.toLowerCase() !== "profile") {
    return {};
  }
  const named = [metaContent(document, "profile:first_name"), metaContent(document, "profile:last_name")]
    .filter(Boolean)
    .join(" ");
  return withoutEmpty<ExtractedPerson>({
    fullName: cleanText(named || metaContent(document, "og:title")),
  });
}

/** `sameAs` is where a bio lists its subject's profiles, and where their LinkedIn usually is. */
function linkedInFrom(sameAs: unknown): string | null {
  const candidates = Array.isArray(sameAs) ? sameAs : [sameAs];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(asText(candidate));
    if (url && isLinkedInProfileUrl(url)) {
      return url;
    }
  }
  return null;
}
