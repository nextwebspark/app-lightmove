import { useQuery } from "@tanstack/react-query";
import * as candidatePrivacyApi from "../api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../api/candidatePrivacyApi";
import { useDataProtection } from "./useDataProtection";

export interface ConsentMode {
  /** The people controls are offered and the workspace asks for consent (#1267); false while either read is pending. */
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
  return { on: offered && firm?.privacyMode === "consent" && firm.readyForNotices, firm };
}
