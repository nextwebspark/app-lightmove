import { z } from "zod";

/**
 * The own-app form's validation, mirroring `UpdateWorkspaceIntegrationRequest` and the service's rules — the
 * client answers instantly, the server holds the line.
 *
 * The secret is never trimmed: it is pasted, and whitespace inside a secret is part of the secret. Blank keeps
 * the one already held, which only an app already saved under the same client ID has — a new client ID needs
 * its own secret.
 */
export function ownAppSchema({ needsTenant, storedSecretClientId }: OwnAppRules) {
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
      if (needsTenant && !values.tenantId) {
        context.addIssue({ code: "custom", path: ["tenantId"], message: "Enter your directory's tenant ID" });
      }
      if (!values.clientSecret.trim() && values.clientId !== storedSecretClientId) {
        context.addIssue({ code: "custom", path: ["clientSecret"], message: "Enter your app's client secret" });
      }
    });
}

export interface OwnAppRules {
  /** Microsoft's single-tenant app signs in at its own directory. */
  needsTenant: boolean;
  /** The client ID the stored secret was saved under, or null when none is held. */
  storedSecretClientId: string | null;
}

export type OwnAppValues = z.infer<ReturnType<typeof ownAppSchema>>;
