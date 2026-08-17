import { z } from "zod";
import { LANGUAGE_OPTIONS, TIMEZONE_OPTIONS } from "./profileOptions";

/**
 * Settings → Profile validation, mirroring `UpdateProfileRequest`'s Bean Validation rules — the
 * client validates to answer instantly, the server because a client can be bypassed with one curl.
 *
 * The two selects are checked against the option lists rather than as free strings, so a stale tab
 * offering a value the server has since stopped accepting fails here with the field named, instead of
 * as a banner after a round-trip.
 */

const timezoneValues = TIMEZONE_OPTIONS.map((option) => option.value);
const languageValues = LANGUAGE_OPTIONS.map((option) => option.value);

export const profileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, "Enter your full name")
    .max(160, "That name is too long"),

  // Optional and free text. Trimmed, and an empty box means "no title" — the server stores null.
  title: z.string().trim().max(120, "That title is too long"),

  timezone: z.string().refine((value) => timezoneValues.includes(value), {
    message: "Pick a timezone from the list",
  }),

  locale: z.string().refine((value) => languageValues.includes(value), {
    message: "Pick a language from the list",
  }),
});

export type ProfileValues = z.infer<typeof profileSchema>;
