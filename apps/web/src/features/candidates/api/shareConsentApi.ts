import { request, requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type { ConsentStatus } from "./types";

/**
 * A position's share-with-client consents (#1267 phase 3): who was asked whether the firm may show their profile to
 * the client, and what they said. Staff-only (the position's WORK_EXECUTE).
 */
export const SHARE_CONSENTS_KEY = (projectId: string) => ["share-consents", projectId] as const;

export interface ShareConsentRow {
  candidateId: string;
  status: ConsentStatus | null;
  requestedAt: string | null;
  requestOpenUntil: string | null;
  answeredAt: string | null;
  clientNamed: boolean;
}

export interface ShareConsents {
  /** The workspace asks for consent to share, so turning on client visibility warns. */
  asksToShare: boolean;
  /** False on a confidential search: its emails never name the client. */
  clientNamable: boolean;
  clientName: string;
  rows: ShareConsentRow[];
}

export function getShareConsents(projectId: string, signal?: AbortSignal): Promise<ShareConsents> {
  return request<ShareConsents>(`/projects/${projectId}/share-consents`, { signal });
}

export function requestShareConsent(projectId: string, candidateId: string, nameClient: boolean): Promise<ShareConsentRow> {
  return request<ShareConsentRow>(`/projects/${projectId}/candidates/${candidateId}/share-consent-request`, {
    method: "POST",
    body: { nameClient },
  });
}

/** The consent register as a CSV, saved straight to disk: the workspace's, or one position's shares. */
export async function saveConsentRegister(projectId?: string): Promise<void> {
  const blob = await requestBlob(
    projectId ? `/projects/${projectId}/export/consent-register` : "/candidates/export/consent-register",
  );
  saveBlob(blob, "uncava-consent-register.csv");
}
