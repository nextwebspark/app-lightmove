import { useQuery } from "@tanstack/react-query";
import * as shareConsentApi from "../api/shareConsentApi";
import type { ShareConsentRow } from "../api/shareConsentApi";
import { useConsentMode } from "./useConsentMode";

export type ShareConsentBadgeState = "none" | "asked" | "given";

export interface ShareConsentState {
  /** The workspace asks for consent to share with the client, so the visibility switch warns (#1267 phase 3). */
  on: boolean;
  clientNamable: boolean;
  rowOf: (candidateId: string) => ShareConsentRow | undefined;
  /** What the card's badge says; never "given" for a consent withdrawn, declined or lapsed. */
  badgeOf: (candidateId: string) => ShareConsentBadgeState;
  /** Turning visibility on should ask first: no consent in force. Warn, never block. */
  needsPrompt: (candidateId: string) => boolean;
}

export function useShareConsents(projectId: string): ShareConsentState {
  const mode = useConsentMode();
  const asks = mode.on && !!mode.firm?.shareWithClient;
  const consents = useQuery({
    queryKey: shareConsentApi.SHARE_CONSENTS_KEY(projectId),
    queryFn: ({ signal }) => shareConsentApi.getShareConsents(projectId, signal),
    enabled: asks,
  });
  const on = asks && !!consents.data?.asksToShare;
  const rows = new Map((consents.data?.rows ?? []).map((row) => [row.candidateId, row]));
  const badgeOf = (candidateId: string): ShareConsentBadgeState => {
    const row = rows.get(candidateId);
    if (row?.status === "given") return "given";
    if (row?.requestOpenUntil) return "asked";
    return "none";
  };
  return {
    on,
    clientNamable: consents.data?.clientNamable ?? false,
    rowOf: (candidateId) => rows.get(candidateId),
    badgeOf,
    needsPrompt: (candidateId) => on && badgeOf(candidateId) !== "given",
  };
}
