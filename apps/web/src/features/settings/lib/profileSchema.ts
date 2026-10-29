import { z } from "zod";
import { DATE_FORMATS, isOfferedTimeZone, TIME_FORMATS } from "../../../lib/datetime";
import { LANGUAGE_OPTIONS } from "./profileOptions";

/**
 * Settings → Profile validation, mirroring `UpdateProfileRequest`'s Bean Validation rules — the
 * client validates to answer instantly, the server because a client can be bypassed with one curl.
 *
 * The selects are checked against the server's own rules rather than as free strings, so a stale tab
 * offering a value the server has since stopped accepting fails here with the field named, instead of
 * as a banner after a round-trip. An empty value is "the workspace's".
 */

const languageValues = LANGUAGE_OPTIONS.map((option) => option.value);

/**
 * The rules for a profile whose own time zone is saved as `savedTimezone`: that zone passes as it is — one saved before
 * the fixed list, like `US/Pacific`, still saves unchanged, as the server lets it — and a new choice must be listed.
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

    timezone: z.string().refine((value) => value === "" || value === savedTimezone || isOfferedTimeZone(value), {
      message: "Pick a timezone from the list",
    }),

    dateFormat: z.enum(["", ...DATE_FORMATS], { message: "Pick a date format from the list" }),

    timeFormat: z.enum(["", ...TIME_FORMATS], { message: "Pick a time format from the list" }),

    locale: z.string().refine((value) => languageValues.includes(value), {
      message: "Pick a language from the list",
    }),
  });
}

export const profileSchema = profileSchemaFor(null);

export type ProfileValues = z.infer<typeof profileSchema>;
