import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CompanyMatch } from "./types";

/**
 * Does the Apollo universe publish the company on this page?
 *
 * A match means the capture is filed under the company's Apollo identity with the snapshot resolved
 * server-side, exactly as the Strategy screen would file it — which is what keeps one company from
 * becoming two rows in a mandate. A miss is an ordinary answer, not an error: the capture goes ahead
 * carrying the page's own fields.
 */
export function resolveCompany(
  api: LightMoveApiClient,
  identity: { domain?: string | null; linkedinUrl?: string | null },
): Promise<CompanyMatch> {
  return api.request<CompanyMatch>("/companies/resolve", {
    query: { domain: identity.domain, linkedinUrl: identity.linkedinUrl },
  });
}
