import { z } from "zod";
import type { IntegrationProvider } from "../api/types";

const GUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CLIENT_ID_CHARACTERS = /^[A-Za-z0-9._-]+$/;
/** A verified domain of the directory; `common`, `organizations` and `consumers` have no dot and fail it. */
const DIRECTORY_DOMAIN = /^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;
const MULTI_TENANT_ALIASES = new Set(["common", "organizations", "consumers"]);
const GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com";
/** Any whitespace (a non-breaking space included), control or invisible format character (a zero-width space, a BOM). */
const NOT_IN_A_SECRET = /[\s\p{Cc}\p{Cf}]/u;
const LONGEST_MICROSOFT_SECRET_MONTHS = 24;

type Refuse = (path: string, message: string) => void;

/**
 * The set-up form's validation, mirroring `WorkspaceIntegrationService` — the client answers instantly, the server
 * holds the line. Every field reports one problem at a time, in the order an admin would fix them.
 *
 * The secret is never trimmed: whitespace inside a secret is the secret. No provider issues one with a space or a line
 * break, though, so one carrying either is a paste that took more than the value, and is refused rather than sent.
 */
export function ownAppSchema({ provider, today }: OwnAppRules) {
  return z
    .object({
      clientId: z.string().trim(),
      clientSecret: z.string(),
      tenantId: z.string().trim(),
      secretExpiresOn: z.string(),
    })
    .superRefine((values, context) => {
      const refuse: Refuse = (path, message) => context.addIssue({ code: "custom", path: [path], message });
      checkClientId(provider, values.clientId, refuse);
      if (provider === "MICROSOFT") checkTenant(values.tenantId, refuse);
      checkSecret(provider, values.clientSecret, refuse);
      if (provider === "MICROSOFT") checkExpiry(values.secretExpiresOn, today, refuse);
    });
}

/** Replace: a new secret for the app already saved, and for Microsoft its own expiry. */
export function replaceSecretSchema({ provider, today }: OwnAppRules) {
  return z
    .object({ clientSecret: z.string(), secretExpiresOn: z.string() })
    .superRefine((values, context) => {
      const refuse: Refuse = (path, message) => context.addIssue({ code: "custom", path: [path], message });
      checkSecret(provider, values.clientSecret, refuse);
      if (provider === "MICROSOFT") checkExpiry(values.secretExpiresOn, today, refuse);
    });
}

function checkClientId(provider: IntegrationProvider, clientId: string, refuse: Refuse) {
  if (!clientId) {
    refuse("clientId", `Enter your app's ${provider === "MICROSOFT" ? "application (client) ID" : "client ID"}`);
  } else if (clientId.length > 255) {
    refuse("clientId", "That client ID is too long");
  } else if (!CLIENT_ID_CHARACTERS.test(clientId)) {
    refuse("clientId", "That doesn't look like a client ID");
  } else if (provider === "GOOGLE" && !clientId.endsWith(GOOGLE_CLIENT_ID_SUFFIX)) {
    refuse("clientId", "A Google client ID ends in .apps.googleusercontent.com");
  } else if (provider === "MICROSOFT" && !GUID.test(clientId)) {
    refuse("clientId", "An application (client) ID is a GUID, as Overview shows it");
  }
}

function checkTenant(tenantId: string, refuse: Refuse) {
  if (!tenantId) {
    refuse("tenantId", "Enter your directory's tenant ID");
  } else if (MULTI_TENANT_ALIASES.has(tenantId.toLowerCase())) {
    refuse("tenantId", `Use your own directory, not ${tenantId.toLowerCase()}`);
  } else if (tenantId.length > 64) {
    refuse("tenantId", "That tenant ID is too long");
  } else if (!GUID.test(tenantId) && !DIRECTORY_DOMAIN.test(tenantId)) {
    refuse("tenantId", "That doesn't look like a tenant ID or domain");
  }
}

function checkSecret(provider: IntegrationProvider, secret: string, refuse: Refuse) {
  if (!secret.trim()) {
    refuse("clientSecret", "Enter your app's client secret");
  } else if (secret.length > 2048) {
    refuse("clientSecret", "That secret is too long");
  } else if (NOT_IN_A_SECRET.test(secret)) {
    refuse("clientSecret", "A secret has no spaces. Check you copied the value alone");
  } else if (provider === "MICROSOFT" && GUID.test(secret)) {
    refuse("clientSecret", "That's the Secret ID. Paste the Value column instead");
  }
}

function checkExpiry(expiresOn: string, today: string, refuse: Refuse) {
  if (!expiresOn) {
    refuse("secretExpiresOn", "Enter when the secret expires");
  } else if (expiresOn < today) {
    refuse("secretExpiresOn", "That date has passed");
  } else if (expiresOn > isoMonthsAfter(today, LONGEST_MICROSOFT_SECRET_MONTHS)) {
    refuse("secretExpiresOn", "Entra secrets last 24 months at most. Check the date");
  }
}

/** Today in the browser, as `yyyy-MM-dd`. */
export function isoToday(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Same calendar day `months` later, clamped to the month's end as `LocalDate.plusMonths` does. */
function isoMonthsAfter(isoDate: string, months: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  return `${target.getUTCFullYear()}-${pad(target.getUTCMonth() + 1)}-${pad(Math.min(day, lastDay))}`;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

export interface OwnAppRules {
  provider: IntegrationProvider;
  /** `yyyy-MM-dd`. */
  today: string;
}

export type OwnAppValues = z.infer<ReturnType<typeof ownAppSchema>>;
export type ReplaceSecretValues = z.infer<ReturnType<typeof replaceSecretSchema>>;
