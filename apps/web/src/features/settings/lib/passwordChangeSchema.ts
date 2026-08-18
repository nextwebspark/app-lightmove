import { z } from "zod";
import { passwordRule } from "../../auth/schemas";

/**
 * Settings → Security's change-password form, mirroring `ChangePasswordRequest`'s server rules.
 *
 * The strength rule is the one from `features/auth/schemas`, not a copy of it — signup, invite accept,
 * reset and this screen all promise the same thing about a password.
 */
export const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: passwordRule,
    confirmPassword: z.string().min(1, "Re-enter your new password"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "Those passwords don't match",
    path: ["confirmPassword"],
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    message: "Choose a password different from your current one",
    path: ["newPassword"],
  });

export type PasswordChangeValues = z.infer<typeof passwordChangeSchema>;
