import { request } from "../../../lib/apiClient";

/**
 * A consultant's email signatures in the session's workspace (`docs/email-signatures.md`): theirs alone, at most one
 * the default the composer ticks. A field a signature leaves empty follows its source — the profile, the workspace or
 * the mailbox — so the server draws every preview, and what it draws is what an email carries.
 */

export const SIGNATURES_KEY = ["email-signatures"] as const;

export const SIGNATURE_LAYOUTS = ["PHOTO_LOGO", "PHOTO", "CLASSIC", "LOGO_BANNER", "MINIMAL", "CUSTOM"] as const;
export type SignatureLayout = (typeof SIGNATURE_LAYOUTS)[number];

/** What a signature says instead of its source; null or blank follows the source. */
export interface SignatureFields {
  name: string | null;
  title: string | null;
  phone: string | null;
  linkedinUrl: string | null;
  company: string | null;
  website: string | null;
  address: string | null;
}

export interface SaveSignaturePayload {
  name: string;
  layout: SignatureLayout;
  fields: SignatureFields;
  showPhoto: boolean;
  showLogo: boolean;
  showBookingLink: boolean;
  customBody: string | null;
  disclaimer: string | null;
  isDefault: boolean;
}

export interface EmailSignature extends SaveSignaturePayload {
  id: string;
  updatedAt: string;
  /** How it draws today, as an email would carry it. */
  html: string;
  text: string;
}

/** What the fields follow where a signature overrides nothing. `photoUrl` is set only for an uploaded photo. */
export interface SignatureSources {
  name: string;
  title: string | null;
  phone: string | null;
  linkedinUrl: string | null;
  email: string;
  company: string;
  website: string | null;
  photoUrl: string | null;
  logoAvailable: boolean;
  bookingLinkOffered: boolean;
}

export interface EmailSignatures {
  signatures: EmailSignature[];
  sources: SignatureSources;
  limit: number;
}

export interface RenderedSignature {
  html: string;
  text: string;
}

export function listSignatures(signal?: AbortSignal): Promise<EmailSignatures> {
  return request<EmailSignatures>("/me/signatures", { signal });
}

export function createSignature(payload: SaveSignaturePayload): Promise<EmailSignature> {
  return request<EmailSignature>("/me/signatures", { method: "POST", body: payload });
}

export function updateSignature(id: string, payload: SaveSignaturePayload): Promise<EmailSignature> {
  return request<EmailSignature>(`/me/signatures/${id}`, { method: "PUT", body: payload });
}

export function makeDefaultSignature(id: string): Promise<EmailSignature> {
  return request<EmailSignature>(`/me/signatures/${id}/default`, { method: "POST" });
}

export function deleteSignature(id: string): Promise<void> {
  return request<void>(`/me/signatures/${id}`, { method: "DELETE" });
}

export function previewSignature(payload: SaveSignaturePayload, signal?: AbortSignal): Promise<RenderedSignature> {
  return request<RenderedSignature>("/me/signatures/preview", { method: "POST", body: payload, signal });
}
