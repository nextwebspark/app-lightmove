import { request } from "../../../lib/apiClient";

/**
 * The public page a privacy notice links to (#923): no session, the token in the link is the credential. It names
 * the firm and the kinds of data it holds, never the values, since a link can be forwarded.
 */
export const PRIVACY_PAGE_KEY = (token: string) => ["privacy-page", token] as const;

export type PrivacyCategory =
  | "name_and_role"
  | "career"
  | "location"
  | "work_email"
  | "personal_email"
  | "phone"
  | "linkedin"
  | "compensation"
  | "background"
  | "notes"
  | "documents"
  | "assessments";

export interface PrivacyPage {
  /** Past its date: only the firm's details come back, so the person still knows whom to write to. */
  expired: boolean;
  controllerName: string | null;
  privacyContactEmail: string | null;
  noticeUrl: string | null;
  lawfulBasis: "legitimate_interest" | "consent" | "contract" | null;
  /** The door the details came through, a `CandidateSource` token. */
  source: string | null;
  heldSince: string | null;
  categories: PrivacyCategory[];
  expiresAt: string;
  /** What the link was sent for: a notice, an email's stop link, a consent request or a manage link (#1267). */
  purpose: PrivacyLinkPurpose;
  /** A request's own purposes, or a manage link's consents in force; empty for a notice. */
  consents: PrivacyPageConsent[];
}

export type PrivacyLinkPurpose = "notice" | "opt_out" | "consent" | "manage";
export type PrivacyPageConsentPurpose = "keep_on_file" | "share_with_client" | "marketing";

export interface PrivacyPageConsent {
  id: string;
  purpose: PrivacyPageConsentPurpose;
  status: "requested" | "given" | "declined" | "withdrawn" | "expired";
  /** Still takes an answer on this link; a given consent that is open is a renewal. */
  open: boolean;
  /** The sentence the person answers, kept with their answer. */
  wording: string | null;
  validUntil: string | null;
  answeredAt: string | null;
  /** How long a yes to keeping their details lasts; null for the other purposes. */
  keepOnFileMonths: number | null;
  roleTitle: string | null;
  clientName: string | null;
}

export type ConsentPageAnswer = "given" | "declined" | "withdrawn";

export type PrivacyRequestKind = "copy" | "deletion";

export function getPrivacyPage(token: string, signal?: AbortSignal): Promise<PrivacyPage> {
  return request<PrivacyPage>(`/privacy/links/${encodeURIComponent(token)}`, { signal, anonymous: true });
}

export function objectFromPrivacyPage(token: string): Promise<void> {
  return request<void>(`/privacy/links/${encodeURIComponent(token)}/objection`, { method: "POST", anonymous: true });
}

/** Stop emails: the same door an outreach email's List-Unsubscribe header posts to. */
export function optOutFromPrivacyPage(token: string): Promise<void> {
  return request<void>(`/privacy/links/${encodeURIComponent(token)}/opt-out`, { method: "POST", anonymous: true });
}

export function requestFromPrivacyPage(token: string, kind: PrivacyRequestKind): Promise<void> {
  return request<void>(`/privacy/links/${encodeURIComponent(token)}/requests`, {
    method: "POST",
    body: { kind },
    anonymous: true,
  });
}

/** Yes or No to each purpose of a consent request, or Withdraw on a manage link. */
export function answerConsents(token: string, answers: { consentId: string; answer: ConsentPageAnswer }[]): Promise<void> {
  return request<void>(`/privacy/links/${encodeURIComponent(token)}/consents`, {
    method: "POST",
    body: { answers },
    anonymous: true,
  });
}
