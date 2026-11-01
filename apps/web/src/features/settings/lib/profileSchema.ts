import { z } from "zod";
import { LANGUAGE_OPTIONS, REGION_ZONE } from "./profileOptions";

/**
 * Settings → Profile validation, mirroring `UpdateProfileRequest`'s Bean Validation rules — the
 * client validates to answer instantly, the server because a client can be bypassed with one curl.
 *
 * The two selects are checked against the server's own rules rather than as free strings, so a stale tab
 * offering a value the server has since stopped accepting fails here with the field named, instead of
 * as a banner after a round-trip.
 */

const languageValues = LANGUAGE_OPTIONS.map((option) => option.value);

/** The server's rule (`ContactDetails`): digits, spaces and + ( ) - . only, a plus only first, six digits at least. */
function isPhoneNumber(value: string): boolean {
  return /^[0-9+().\- ]+$/.test(value) && !value.slice(1).includes("+") && value.replace(/\D/g, "").length >= 6;
}

const LINKEDIN_PROFILE = /^(https?:\/\/)?([a-z0-9-]+\.)*linkedin\.com\/in\/[^/?#\s]+/i;

/**
 * The rules for a profile whose time zone is saved as `savedTimezone`: that zone passes as it is — one saved before the
 * region rule, like `US/Pacific`, still saves unchanged, as the server lets it — and a new choice must be a region's.
 */
export function profileSchemaFor(savedTimezone: string | null) {
  return z.object({
    fullName: z
      .string()
      .trim()
      .min(1, "Enter your full name")
      .max(160, "That name is too long"),

    // Optional and free text. Trimmed, and an empty box means "no title" — the server stores null.
    title: z.string().trim().max(120, "That title is too long"),

    timezone: z.string().refine((value) => value === savedTimezone || REGION_ZONE.test(value), {
      message: "Pick a timezone from the list",
    }),

    locale: z.string().refine((value) => languageValues.includes(value), {
      message: "Pick a language from the list",
    }),

    // Both optional, shown in the email signature; an empty box clears them.
    phone: z
      .string()
      .trim()
      .max(40, "That phone number is too long")
      .refine((value) => value === "" || isPhoneNumber(value), { message: "Enter a phone number" }),

    linkedinUrl: z
      .string()
      .trim()
      .max(255, "That address is too long")
      .refine((value) => value === "" || LINKEDIN_PROFILE.test(value), {
        message: "Enter your LinkedIn profile address, like linkedin.com/in/your-name",
      }),
  });
}

export const profileSchema = profileSchemaFor(null);

export type ProfileValues = z.infer<typeof profileSchema>;
