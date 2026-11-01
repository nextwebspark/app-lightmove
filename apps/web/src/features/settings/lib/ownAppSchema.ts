import { z } from "zod";
import type { IntegrationProvider } from "../api/types";

const GUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** A verified domain of the directory; `common`, `organizations` and `consumers` have no dot and fail it. */
const DIRECTORY_DOMAIN = /^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;
const GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com";
/** Any whitespace (a non-breaking space included) or control character. */
const NOT_IN_A_SECRET = /[\s\p{Cc}]/u;
const LONGEST_MICROSOFT_SECRET_MONTHS = 24;

/**
 * The own-app form's validation, mirroring `WorkspaceIntegrationService` — the client answers instantly, the server
 * holds the line.
 *
 * The secret is never trimmed: whitespace inside a secret is the secret. No provider issues one with a space or a line
 * break, though, so one carrying either is a paste that took more than the value, and is refused rather than sent.
 * Blank keeps the one already held, which only an app already saved under the same client ID has — a new client ID
 * needs its own secret.
 */
export function ownAppSchema({ provider, storedSecretClientId, storedExpiresOn, today }: OwnAppRules) {
  const isMicrosoft = provider === "MICROSOFT";
  return z
    .object({
      clientId: z
        .string()
        .trim()
        .min(1, "Enter your app's client ID")
        .max(255, "That client ID is too long")
        .regex(/^[A-Za-z0-9._-]*$/, "That doesn't look like a client ID"),
      clientSecret: z.string().max(2048, "That secret is too long"),
      tenantId: z
        .string()
        .trim()
        .max(64, "That tenant ID is too long")
        .regex(/^[A-Za-z0-9.-]*$/, "That doesn't look like a tenant ID"),
      secretExpiresOn: z.string(),
    })
    .superRefine((values, context) => {
      const refuse = (path: keyof OwnAppValues, message: string) =>
        context.addIssue({ code: "custom", path: [path], message });

      if (provider === "GOOGLE" && values.clientId && !values.clientId.endsWith(GOOGLE_CLIENT_ID_SUFFIX)) {
        refuse("clientId", "A Google client ID ends in .apps.googleusercontent.com");
      }
      if (isMicrosoft && values.clientId && !GUID.test(values.clientId)) {
        refuse("clientId", "Paste the application (client) ID from the app's Overview");
      }

      if (isMicrosoft && !values.tenantId) {
        refuse("tenantId", "Enter your directory's tenant ID");
      } else if (isMicrosoft && !GUID.test(values.tenantId) && !DIRECTORY_DOMAIN.test(values.tenantId)) {
        refuse("tenantId", "Enter your directory's ID or primary domain");
      }

      const secret = values.clientSecret;
      if (!secret.trim()) {
        if (values.clientId !== storedSecretClientId) {
          refuse("clientSecret", "Enter your app's client secret");
        }
      } else if (NOT_IN_A_SECRET.test(secret)) {
        refuse("clientSecret", "A client secret has no spaces or line breaks. Copy its value alone");
      } else if (isMicrosoft && GUID.test(secret)) {
        refuse("clientSecret", "That is the secret's ID. Paste its value instead");
      }

      const expiresOn = values.secretExpiresOn;
      if (isMicrosoft && expiresOn && expiresOn !== storedExpiresOn) {
        if (expiresOn < today) {
          refuse("secretExpiresOn", "That date has passed");
        } else if (expiresOn > isoMonthsAfter(today, LONGEST_MICROSOFT_SECRET_MONTHS)) {
          refuse("secretExpiresOn", "A Microsoft secret lasts 24 months at most");
        }
      }
    });
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
  /** The client ID the stored secret was saved under, or null when none is held. */
  storedSecretClientId: string | null;
  /** The expiry already on the record, which stands even once it has passed, so the app can still be corrected. */
  storedExpiresOn: string | null;
  /** `yyyy-MM-dd`. */
  today: string;
}

export type OwnAppValues = z.infer<ReturnType<typeof ownAppSchema>>;
