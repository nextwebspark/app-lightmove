import { formatInstantDate } from "../../../lib/format";
import type { ApiKey, ApiKeyKind, ApiKeyScope } from "../api/types";

/** What each scope reads, in the API's own order; the last two are personal data and say so. */
export const API_KEY_SCOPES: readonly { scope: ApiKeyScope; note: string; personalData: boolean }[] = [
  { scope: "projects:read", note: "Positions: title, stage, type, dates and counts.", personalData: false },
  { scope: "companies:read", note: "Each position's companies — in universe, shortlisted and declined.", personalData: false },
  { scope: "candidates:read", note: "Each position's executives: profile, company and status.", personalData: false },
  { scope: "candidates.contacts:read", note: "Their emails and phone numbers.", personalData: true },
  { scope: "candidates.compensation:read", note: "Their salary, bonus, allowances and incentives.", personalData: true },
];

/** The wire's `SERVICE` is a "Workspace" key on screen. */
export const KIND_LABEL: Record<ApiKeyKind, string> = { PERSONAL: "Personal", SERVICE: "Workspace" };

export const DEFAULT_SCOPES: readonly ApiKeyScope[] = ["projects:read", "companies:read", "candidates:read"];

export const EXPIRY_CHOICES = [
  { days: 30, label: "In 30 days" },
  { days: 90, label: "In 90 days" },
  { days: 180, label: "In 180 days" },
  { days: 365, label: "In 1 year" },
] as const;

/** A live key this close to its expiry is flagged, so whoever owns the tool makes the next one in time. */
const EXPIRING_SOON_DAYS = 14;

export type ApiKeyTone = "active" | "soon" | "expired" | "revoked";

export function isPersonalData(scope: ApiKeyScope): boolean {
  return API_KEY_SCOPES.some((choice) => choice.scope === scope && choice.personalData);
}

export function statusOf(key: ApiKey, now: Date = new Date()): { label: string; tone: ApiKeyTone } {
  if (key.status === "REVOKED") return { label: "Revoked", tone: "revoked" };
  if (key.status === "EXPIRED") return { label: "Expired", tone: "expired" };
  const days = Math.ceil((new Date(key.expiresAt).getTime() - now.getTime()) / 86_400_000);
  if (days <= 0) return { label: "Expired", tone: "expired" };
  if (days <= EXPIRING_SOON_DAYS) {
    return { label: `Expires in ${days} ${days === 1 ? "day" : "days"}`, tone: "soon" };
  }
  return { label: "Active", tone: "active" };
}

/** "created 2 Sep 2026 · expires 1 Dec 2026", with whose key it is on the All view. */
export function metaLineOf(key: ApiKey, showOwner: boolean): string {
  const parts: string[] = [];
  if (showOwner && key.kind === "PERSONAL") parts.push(key.ownerName ?? "A former member");
  parts.push(
    `created ${formatInstantDate(key.createdAt)}` +
      (key.kind === "SERVICE" && key.createdByName ? ` by ${key.createdByName}` : ""),
  );
  if (key.status === "REVOKED") {
    parts.push(`revoked ${formatInstantDate(key.revokedAt)}` + (key.revokedByName ? ` by ${key.revokedByName}` : ""));
  } else {
    parts.push(`${key.status === "EXPIRED" ? "expired" : "expires"} ${formatInstantDate(key.expiresAt)}`);
  }
  return parts.join(" · ");
}

export function usageLineOf(key: ApiKey, now: Date = new Date()): string {
  if (!key.lastUsedAt) return "Never used";
  return `Last used ${sinceLabel(key.lastUsedAt, now)}${key.lastUsedIp ? ` from ${key.lastUsedIp}` : ""}`;
}

function sinceLabel(isoInstant: string, now: Date): string {
  const minutes = Math.floor((now.getTime() - new Date(isoInstant).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return minutes === 1 ? "1 minute ago" : `${minutes} minutes ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours === 1 ? "1 hour ago" : `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "yesterday" : `${days} days ago`;
}

export function expiryDateAfter(days: number, now: Date = new Date()): string {
  const at = new Date(now.getTime() + days * 86_400_000);
  return formatInstantDate(at.toISOString()) ?? "";
}
