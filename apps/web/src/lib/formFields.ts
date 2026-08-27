import { z } from "zod";
import { toBrowsableUrl } from "./url";

/**
 * Zod pieces for the two field shapes a text input gets wrong on its own, shared by every form that
 * collects optional research: a company capture, a candidate profile, and whatever collects the next
 * one. Both are about the same thing — an untouched input posts `""`, which is not a value.
 */

/**
 * A number typed into a text input arrives as a string, and an untouched one as `""`. Coercing
 * blindly would send `NaN`, and `z.coerce.number()` reads `""` as 0 — a company with no published
 * headcount would be filed as having none, which is a different claim. So: empty means omitted.
 */
export const optionalNumber = (label: string, max: number) =>
  z
    .string()
    .trim()
    .transform((value) => (value === "" ? undefined : Number(value)))
    .refine((value) => value === undefined || (Number.isFinite(value) && value >= 0), {
      message: `${label} must be a number`,
    })
    .refine((value) => value === undefined || value <= max, {
      message: `That ${label.toLowerCase()} looks like a typo`,
    });

/**
 * The server drops an address it cannot parse rather than refusing the whole write, which is right
 * for the plugin — a bad URL should not cost the record — and wrong for a form, where it means a typo
 * posts, toasts success, and vanishes. So a form holds itself to the stricter rule: what the server
 * would keep, it accepts; what the server would drop, it refuses while the field is still on screen.
 */
export const optionalWebAddress = (label: string) =>
  z
    .string()
    .trim()
    .max(500)
    .refine((value) => value === "" || toBrowsableUrl(value) !== null, {
      message: `That ${label} does not look like a web address`,
    });
