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
}

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
