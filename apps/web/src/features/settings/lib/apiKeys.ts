import { formatInstantDate } from "../../../lib/format";
import type { ApiKey, ApiKeyKind, ApiKeyScope } from "../api/types";

interface ApiKeyScopeChoice {
  scope: ApiKeyScope;
  note: string;
  personalData: boolean;
  opensMcp: boolean;
}

/**
 * What each scope reads, in the API's own order; the personal-data pair says so. `mcp:use` reads nothing: it lets an AI
 * agent use the key over MCP, with whatever else the key reads.
 */
export const API_KEY_SCOPES: readonly ApiKeyScopeChoice[] = [
  {
    scope: "projects:read",
    note: "Positions: title, stage, type, dates and counts.",
    personalData: false,
    opensMcp: false,
  },
  {
    scope: "companies:read",
    note: "Each position's companies — in universe, shortlisted and declined.",
    personalData: false,
    opensMcp: false,
  },
  {
    scope: "candidates:read",
    note: "Each position's executives: profile, company and status.",
    personalData: false,
    opensMcp: false,
  },
  {
    scope: "candidates.contacts:read",
    note: "Their emails and phone numbers.",
    personalData: true,
    opensMcp: false,
  },
  {
    scope: "candidates.compensation:read",
    note: "Their salary, bonus, allowances and incentives.",
    personalData: true,
    opensMcp: false,
  },
  {
    scope: "mcp:use",
    note: "Lets an AI agent use this key over MCP. Reads nothing by itself — tick what it may read too.",
    personalData: false,
    opensMcp: true,
  },
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

/** A scope as a chip: personal data in the off-limits tone, `mcp:use` in the accent, the rest plain. */
export function scopeChipClass(scope: ApiKeyScope): string {
  if (isPersonalData(scope)) return "bg-u-offlimits-tint text-u-offlimits";
  return scope === "mcp:use" ? "bg-u-accent-tint text-u-accent" : "bg-u-surface text-u-text2";
}

/** A key holding only `mcp:use` would reach the MCP server and read nothing there. */
export function readsSomething(scopes: readonly ApiKeyScope[]): boolean {
  return scopes.some((scope) => scope !== "mcp:use");
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

export function sinceLabel(isoInstant: string, now: Date = new Date()): string {
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
