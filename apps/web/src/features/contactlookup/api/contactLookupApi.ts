import { request } from "../../../lib/apiClient";
import type { Candidate } from "../../candidates/api/types";

/**
 * Finding an executive's email or phone — the drawer's two Find buttons.
 *
 * <p>Two calls rather than one taking a channel, because the provider bills them from separate pools:
 * one can be refused for want of credits while the other still answers.
 */

/** Whether this deployment looks contacts up at all. A deployment fact, so it is read once. */
export const CONTACT_LOOKUP_CONFIG_KEY = ["contactLookup", "config"] as const;

export interface ContactLookupConfig {
  enabled: boolean;
}

/** `held` and `none` both mean nothing was asked and nothing was spent. */
export type ContactLookupOutcome = "found" | "none" | "held";

export interface ContactLookupResult {
  outcome: ContactLookupOutcome;
  candidate: Candidate;
}

export function getContactLookupConfig(signal?: AbortSignal): Promise<ContactLookupConfig> {
  return request<ContactLookupConfig>("/contact-lookup/config", { signal });
}

export function findEmail(projectId: string, candidateId: string): Promise<ContactLookupResult> {
  return request<ContactLookupResult>(contactUrl(projectId, candidateId, "email"), { method: "POST" });
}

export function findPhone(projectId: string, candidateId: string): Promise<ContactLookupResult> {
  return request<ContactLookupResult>(contactUrl(projectId, candidateId, "phone"), { method: "POST" });
}

function contactUrl(projectId: string, candidateId: string, channel: "email" | "phone") {
  return `/projects/${projectId}/candidates/${candidateId}/contact/${channel}`;
}
