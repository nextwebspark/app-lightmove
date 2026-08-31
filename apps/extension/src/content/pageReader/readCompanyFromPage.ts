import { mergeExtracted, type ExtractedCompany } from "./extractedCompany";
import { companyDirectoryExtractor } from "./extractors/companyDirectoryExtractor";
import { linkedInCompanyExtractor } from "./extractors/linkedInCompanyExtractor";
import { structuredDataExtractor } from "./extractors/structuredDataExtractor";

/**
 * Reads the company off a page. Every extractor runs and decides for itself whether it understands the
 * page; order is priority, most specific first, and the merge takes the first non-empty value — so a
 * reader appended here can only fill gaps.
 */
export function readCompanyFromPage(document: Document): ExtractedCompany {
  const company = mergeExtracted([
    linkedInCompanyExtractor(document),
    companyDirectoryExtractor(document),
    structuredDataExtractor(document),
  ]);

  // A company's own site is the strongest claim to its domain there is, and on such a site no
  // extractor above will have said so — og:url and canonical are about the page, and both are often
  // missing entirely. Falling back to the address bar is what makes the ordinary case work.
  if (!company.website && document.location && /^https?:$/.test(document.location.protocol)) {
    const host = document.location.hostname;
    if (host && !isAggregatorHost(host)) {
      company.website = `${document.location.protocol}//${host}`;
    }
  }
  return company;
}

/**
 * Hosts whose address bar must never become the captured company's website — a LinkedIn page is not
 * the company's site, and filing one under `linkedin.com` would key every company captured there to
 * the same domain and collapse them into a single triage row.
 */
function isAggregatorHost(host: string): boolean {
  return /(^|\.)(linkedin\.com|apollo\.io|crunchbase\.com|google\.[a-z.]+|bing\.com)$/i.test(host);
}
