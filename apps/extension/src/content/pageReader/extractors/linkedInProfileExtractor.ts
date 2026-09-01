import { cleanText, httpUrlOrNull, withoutEmpty } from "../extractedCompany";
import { isLinkedInProfileUrl, type ExtractedPerson, type PersonExtractor } from "../extractedPerson";
import { textOf } from "./dom";

/**
 * `linkedin.com/in/*` — the name off a profile, and nothing more. The signed-in 2025 layout renders
 * no `h1`, hashes every class name per deploy and serves no JSON-LD, so anything richer than the
 * name proved unshippable; the tab title is the one anchor every layout still offers.
 */
export const linkedInProfileExtractor: PersonExtractor = (document) => {
  if (!isLinkedInProfilePage(document)) {
    return {};
  }
  const extracted: Partial<ExtractedPerson> = {
    fullName: cleanText(
      textOf(document, 'h1[class*="top-card-layout__title"]')
        ?? textOf(document, "main h1")
        ?? textOf(document, "h1"),
    ) ?? nameFromDocumentTitle(document),
    linkedinUrl: canonicalProfileUrl(document),
  };
  return withoutEmpty(extracted);
};

/**
 * The person's name, out of the tab title: "(3) Amira Haddad - Group CFO - Al Rawabi | LinkedIn",
 * where the count is unread notifications and the separator has shipped as a hyphen, an en dash and
 * an em dash. The most stable signal there is — it survives every layout so far.
 */
function nameFromDocumentTitle(document: Document): string | null {
  const titleText = cleanText(document.title)
    ?.replace(/^\(\d+\+?\)\s*/, "")
    .replace(/\s*[|│]\s*LinkedIn\s*$/i, "");
  const parts = titleText?.split(/\s+[-–—]\s+/).map((part) => part.trim()).filter(Boolean) ?? [];
  return parts[0] ?? null;
}

/**
 * Keyed on the host the browser actually loaded, never on `canonical`/`og:url` — those are page-supplied,
 * so any site could otherwise declare itself a LinkedIn profile and be read by this extractor.
 */
function isLinkedInProfilePage(document: Document): boolean {
  const hostname = document.location?.hostname ?? "";
  return /(^|\.)linkedin\.com$/i.test(hostname) && (document.location?.pathname ?? "").startsWith("/in/");
}

function canonicalProfileUrl(document: Document): string | null {
  const candidates = [
    document.querySelector('link[rel="canonical"]')?.getAttribute("href"),
    document.querySelector('meta[property="og:url"]')?.getAttribute("content"),
    document.location?.href,
  ];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(candidate);
    if (url && isLinkedInProfileUrl(url)) {
      return url;
    }
  }
  return null;
}
