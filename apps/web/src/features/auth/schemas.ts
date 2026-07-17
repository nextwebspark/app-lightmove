import { z } from "zod";

/**
 * Client-side validation, mirroring the server's Bean Validation rules.
 *
 * The duplication is deliberate and is not a redundancy to be refactored away. The client validates to
 * answer the user instantly, without a round-trip. The server validates because the client can be
 * bypassed with a single curl command. Neither can be dropped, and the messages are kept identical so
 * a rule that only the server catches does not suddenly sound like a different product.
 */

/** The mockup's own words: "Use at least 8 characters, with one number." */
const password = z
  .string()
  .min(8, "Use at least 8 characters")
  .max(72, "Use at most 72 characters")
  .regex(/\d/, "Include at least one number");

const email = z
  .string()
  .min(1, "Enter your work email")
  .email("That doesn't look like a valid email");

export const loginSchema = z.object({
  email: z.string().min(1, "Enter your email").email("That doesn't look like a valid email"),
  password: z.string().min(1, "Enter your password"),
});

export const signupSchema = z
  .object({
    fullName: z.string().min(1, "Enter your full name").max(160, "That name is too long"),
    email,
    password,
    confirmPassword: z.string().min(1, "Re-enter your password"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "Those passwords don't match",
    path: ["confirmPassword"],
  });

/**
 * Accepting an invitation: the invitee sets a name and a password (entered twice). No email — it is the
 * invitation's, shown read-only, never theirs to choose.
 */
export const acceptInviteSchema = z
  .object({
    fullName: z.string().min(1, "Enter your full name").max(160, "That name is too long"),
    password,
    confirmPassword: z.string().min(1, "Re-enter your password"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "Those passwords don't match",
    path: ["confirmPassword"],
  });

export const workspaceSchema = z.object({
  name: z.string().min(1, "Enter your organization's name").max(160, "That name is too long"),
  companySize: z.string(),
  primaryRegion: z.string(),
  teamFocus: z.string(),
});

export const inviteSchema = z.object({
  invites: z.array(
    z.object({
      // Blank rows are allowed and skipped on submit — the mockup starts with two empty rows, and
      // refusing to continue because the user did not fill them in would be absurd.
      email: z.string().refine((value) => value === "" || z.string().email().safeParse(value).success, {
        message: "That doesn't look like a valid email",
      }),
      role: z.enum(["ADMIN", "MEMBER"]),
    }),
  ),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type SignupValues = z.infer<typeof signupSchema>;
export type AcceptInviteValues = z.infer<typeof acceptInviteSchema>;
export type WorkspaceValues = z.infer<typeof workspaceSchema>;
export type InviteValues = z.infer<typeof inviteSchema>;

/** The dropdown options, taken from Signup.dc.html. */
export const COMPANY_SIZES = ["1–10 people", "11–50 people", "51–200 people", "200+ people"];
export const REGIONS = ["GCC", "MENA", "Europe", "North America", "Global"];
export const TEAM_FOCUSES = ["Executive search", "Board advisory", "Talent mapping", "Mixed"];
export const INVITE_ROLES = ["MEMBER", "ADMIN"] as const;
