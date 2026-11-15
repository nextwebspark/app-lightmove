import { useQuery } from "@tanstack/react-query";
import * as candidatePrivacyApi from "../api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../api/candidatePrivacyApi";
import { useDataProtection } from "./useDataProtection";

export interface ConsentMode {
  /**
   * The people controls are offered, the workspace asks for consent (#1267) and has a purpose to ask about here; false
   * while either read is pending. A share is asked from its position, so it alone offers nothing on these screens.
   */
  on: boolean;
  firm: CandidatePrivacySettings | undefined;
}

export function useConsentMode(): ConsentMode {
  const { offered } = useDataProtection();
  const settings = useQuery({
    queryKey: candidatePrivacyApi.CANDIDATE_PRIVACY_KEY,
    queryFn: ({ signal }) => candidatePrivacyApi.getCandidatePrivacy(signal),
    enabled: offered,
  });
  const firm = settings.data;
  return {
    on:
      offered &&
      firm?.privacyMode === "consent" &&
      firm.readyForNotices &&
      firm.readyForConsent &&
      (firm.keepOnFile || firm.marketing),
    firm,
  };
}
