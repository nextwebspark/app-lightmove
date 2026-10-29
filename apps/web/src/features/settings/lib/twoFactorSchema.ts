import { z } from "zod";
import { isAuthenticatorCode, secondFactorCodeRule } from "../../auth/schemas";

const currentPassword = z.string().min(1, "Enter your current password");

/** Turning it on proves the app holds the new secret, so only the app's code will do. */
export const twoFactorEnableSchema = z.object({
  currentPassword,
  code: z.string().refine(isAuthenticatorCode, "Enter the 6-digit code from your authenticator app"),
});

/** Turning it off, or new recovery codes: the app's code or a recovery code, whichever is to hand. */
export const twoFactorChangeSchema = z.object({
  currentPassword,
  code: secondFactorCodeRule,
});

export type TwoFactorChangeValues = z.infer<typeof twoFactorChangeSchema>;
