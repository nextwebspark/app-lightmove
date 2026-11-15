import { request } from "../../../lib/apiClient";
import type { LawfulBasis } from "./types";

/**
 * The firm as controller of its candidates (#923): named in every privacy notice, its contact taking the replies, its
 * own notice linked. Read by anyone who sends a notice; changed by an admin under Settings → Data & retention.
 */
export const CANDIDATE_PRIVACY_KEY = ["candidate-privacy"] as const;

export interface CandidatePrivacySettings {
  controllerName: string | null;
  privacyContactEmail: string | null;
  noticeUrl: string | null;
  defaultLawfulBasis: LawfulBasis;
  /** The firm's name, contact and notice link are all set: notices can go. */
  readyForNotices: boolean;
  privacyMode: CandidatePrivacyMode;
  keepOnFile: boolean;
  keepOnFileMonths: KeepOnFileMonths;
  shareWithClient: boolean;
  marketing: boolean;
  /** Days before a keep-on-file consent ends that it is asked about again; 0 never asks. */
  renewalDaysBefore: number;
  /** Consent mode is on, the firm's details are set, and at least one purpose is offered (#1267). */
  readyForConsent: boolean;
}

/** Notices alone, or notices and consent requests per purpose (#1267). */
export type CandidatePrivacyMode = "notices" | "consent";

export type KeepOnFileMonths = 12 | 24 | 36;

export interface CandidatePrivacySettingsChange {
  controllerName: string;
  privacyContactEmail: string;
  noticeUrl: string;
  defaultLawfulBasis: LawfulBasis;
  privacyMode: CandidatePrivacyMode;
  keepOnFile: boolean;
  keepOnFileMonths: KeepOnFileMonths;
  shareWithClient: boolean;
  marketing: boolean;
  renewalDaysBefore: number;
}

export function getCandidatePrivacy(signal?: AbortSignal): Promise<CandidatePrivacySettings> {
  return request<CandidatePrivacySettings>("/workspace/candidate-privacy", { signal });
}

export function putCandidatePrivacy(change: CandidatePrivacySettingsChange): Promise<CandidatePrivacySettings> {
  return request<CandidatePrivacySettings>("/workspace/candidate-privacy", { method: "PUT", body: change });
}
