import { z } from "zod";
import { isAuthenticatorCode, secondFactorCodeRule } from "../../auth/schemas";

const currentPassword = z.string().min(1, "Enter your current password");
/** An account with no password has none to give: it proves itself by the code, or by having just signed in. */
const noPassword = z.string();

const authenticatorCode = z.string().refine(isAuthenticatorCode, "Enter the 6-digit code from your authenticator app");

/** Turning it on proves the app holds the new secret, so only the app's code will do. */
export const twoFactorEnableSchema = z.object({ currentPassword, code: authenticatorCode });

/** Turning it off, or new recovery codes: the app's code or a recovery code, whichever is to hand. */
export const twoFactorChangeSchema = z.object({ currentPassword, code: secondFactorCodeRule });

export function twoFactorSchemaFor({ authenticatorOnly, hasPassword }: { authenticatorOnly: boolean; hasPassword: boolean }) {
  if (hasPassword) return authenticatorOnly ? twoFactorEnableSchema : twoFactorChangeSchema;
  return z.object({ currentPassword: noPassword, code: authenticatorOnly ? authenticatorCode : secondFactorCodeRule });
}

export type TwoFactorChangeValues = z.infer<typeof twoFactorChangeSchema>;
