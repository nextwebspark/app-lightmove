import { zodResolver } from "@hookform/resolvers/zod";
import type { Resolver } from "react-hook-form";
import { z } from "zod";
import { formatNumber } from "../../../lib/format";
import { optionalNumber, optionalWebAddress } from "../../../lib/formFields";
import type {
  Candidate,
  CandidateSeniority,
  CandidateStatus,
  SaveCandidatePayload,
} from "../api/types";

/**
 * The executive profile as a form: one schema, cut into the sections the panel edits one at a time.
 *
 * <p>Only the name is required, matching the server. Research arrives in pieces — a name, a company
 * and a rough title from a conference — and demanding a complete profile would send that name into a
 * spreadsheet, which is what these screens exist to replace.
 */
export const candidateSchema = z.object({
  fullName: z.string().trim().min(1, "A name is required").max(200),
  title: z.string().trim().max(200),
  seniority: z.string(),
  status: z.string(),
  employerName: z.string().trim().max(200),
  email: z
    .string()
    .trim()
    .max(320)
    .refine((value) => value === "" || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value), {
      message: "That doesn't look like a valid email",
    }),
  phone: z.string().trim().max(50),
  linkedinUrl: optionalWebAddress("LinkedIn URL"),
  locationCountry: z.string().trim().max(100),
  locationCity: z.string().trim().max(100),
  nationality: z.string().trim().max(100),
  yearsExperience: optionalNumber("Years of experience", 70),
  summary: z.string().trim().max(4000),
  note: z.string().trim().max(2000),
  // Wider than the old 400: enrichment writes this field too, and a form that refuses to save a
  // package because research listed nine languages would block the one section it was opened for.
  languages: z.string().trim().max(1200),
  currency: z.string().trim().max(3),
  baseSalary: money("Base salary"),
  bonus: money("Bonus"),
  allowances: money("Allowances"),
  longTermIncentive: money("Long-term incentive"),
  noticePeriod: z.string().trim().max(100),
  career: z
    .array(
      z.object({
        company: z.string().trim().max(200),
        title: z.string().trim().max(200),
        period: z.string().trim().max(60),
      }),
    )
    .max(25, "A career history holds 25 posts at most"),
});

/** A figure typed with the thousands separators the field shows it with — "420,000" is 420000. */
function money(label: string) {
  return z
    .string()
    .transform((value) => value.replace(/[,\s]/g, ""))
    .pipe(optionalNumber(label, Number.MAX_SAFE_INTEGER));
}

/** What the inputs hold: every scalar a string, because that is what a text input gives back. */
export type CandidateForm = z.input<typeof candidateSchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
export type ParsedCandidateForm = z.output<typeof candidateSchema>;

export const EMPTY_FORM: CandidateForm = {
  fullName: "",
  title: "",
  seniority: "",
  status: "identified",
  employerName: "",
  email: "",
  phone: "",
  linkedinUrl: "",
  locationCountry: "",
  locationCity: "",
  nationality: "",
  yearsExperience: "",
  summary: "",
  note: "",
  languages: "",
  currency: "",
  baseSalary: "",
  bonus: "",
  allowances: "",
  longTermIncentive: "",
  noticePeriod: "",
  career: [],
};

/**
 * The profile's sections and the fields each one edits. A section is saved on its own, so this is
 * also the list of what one save may change — everything outside it is replayed from the stored
 * profile untouched. Status is in no section: it has a write of its own, and a section that carried
 * it would let a form opened five minutes ago undo a pill flicked since.
 */
export const SECTION_FIELDS = {
  identity: [
    "fullName",
    "title",
    "employerName",
    "seniority",
    "locationCity",
    "locationCountry",
  ],
  summary: ["summary"],
  experience: ["career"],
  compensation: ["currency", "baseSalary", "bonus", "allowances", "longTermIncentive", "noticePeriod"],
  background: ["nationality", "yearsExperience", "languages"],
  contact: ["email", "phone", "linkedinUrl"],
  note: ["note"],
} as const satisfies Record<string, readonly (keyof CandidateForm)[]>;

export type ProfileFormSection = keyof typeof SECTION_FIELDS;

type SectionKey<S extends ProfileFormSection> = (typeof SECTION_FIELDS)[S][number];

/** What one section's form hands back: its own fields, parsed, and nothing else. */
export type SectionValues<S extends ProfileFormSection> = Pick<ParsedCandidateForm, SectionKey<S>>;

/**
 * Validates one section of a form that holds the whole profile. The other fields ride along as the
 * stored values and are neither checked nor returned: a section is saved on what it shows, and a
 * stale figure enrichment wrote into Contact must not be able to block a Compensation save with an
 * error nobody can see. The cast is the one place the picked schema meets the full form type.
 */
export function sectionResolver<S extends ProfileFormSection>(
  section: S,
): Resolver<CandidateForm, unknown, SectionValues<S>> {
  const mask = Object.fromEntries(SECTION_FIELDS[section].map((key) => [key, true])) as {
    [K in keyof CandidateForm]?: true;
  };
  return zodResolver(candidateSchema.pick(mask)) as unknown as Resolver<
    CandidateForm,
    unknown,
    SectionValues<S>
  >;
}

/** The stored profile, back in the shapes the inputs hold: strings, and a comma-joined language list. */
export function formOf(candidate: Candidate): CandidateForm {
  return {
    fullName: candidate.fullName,
    title: candidate.title ?? "",
    seniority: candidate.seniority ?? "",
    status: candidate.status,
    employerName: candidate.companyName ?? "",
    email: candidate.email ?? "",
    phone: candidate.phone ?? "",
    linkedinUrl: candidate.linkedinUrl ?? "",
    locationCountry: candidate.locationCountry ?? "",
    locationCity: candidate.locationCity ?? "",
    nationality: candidate.nationality ?? "",
    yearsExperience: candidate.yearsExperience?.toString() ?? "",
    summary: candidate.summary ?? "",
    note: candidate.note ?? "",
    languages: candidate.languages.join(", "),
    currency: candidate.compensation.currency ?? "",
    baseSalary: amountOf(candidate.compensation.baseSalary),
    bonus: amountOf(candidate.compensation.bonus),
    allowances: amountOf(candidate.compensation.allowances),
    longTermIncentive: amountOf(candidate.compensation.longTermIncentive),
    noticePeriod: candidate.compensation.noticePeriod ?? "",
    career: candidate.career.map((entry) => ({
      company: entry.company ?? "",
      title: entry.title ?? "",
      period: entry.period ?? "",
    })),
  };
}

/** A stored figure as the amount field shows it, with thousands separators. */
export function amountOf(amount: number | null): string {
  return amount === null ? "" : formatNumber(amount);
}

/**
 * The stored profile as the request that would store it again unchanged. A section save is this
 * with one section's fields written over it: the server replaces the whole record, so what the
 * panel is not editing has to be said again exactly as it is.
 *
 * <p>Custom columns are deliberately left out — omitted, the server leaves every one of them alone,
 * so a save of any other section cannot touch them.
 */
export function replayOf(candidate: Candidate): SaveCandidatePayload {
  return {
    triageCompanyId: candidate.triageCompanyId,
    fullName: candidate.fullName,
    title: candidate.title ?? undefined,
    seniority: candidate.seniority ?? undefined,
    status: candidate.status,
    employerName: candidate.triageCompanyId ? undefined : (candidate.companyName ?? undefined),
    email: candidate.email ?? undefined,
    phone: candidate.phone ?? undefined,
    linkedinUrl: candidate.linkedinUrl ?? undefined,
    locationCountry: candidate.locationCountry ?? undefined,
    locationCity: candidate.locationCity ?? undefined,
    nationality: candidate.nationality ?? undefined,
    yearsExperience: candidate.yearsExperience ?? undefined,
    summary: candidate.summary ?? undefined,
    note: candidate.note ?? undefined,
    compensation: { ...candidate.compensation },
    career: candidate.career.map((entry) => ({ ...entry })),
    languages: [...candidate.languages],
  };
}

/**
 * One section's parsed fields as the request keys they set. Empty strings become omissions rather
 * than blanks — the server treats a blank as null anyway, and sending `""` would make the network
 * log lie about what was typed.
 *
 * <p>Identity's employer is only sent for someone mapped to no company: where a company row is
 * named the server snapshots that company's name and the two must not be able to disagree.
 */
const PATCHES: {
  [S in ProfileFormSection]: (
    parsed: SectionValues<S>,
    mapped: boolean,
  ) => Partial<SaveCandidatePayload>;
} = {
  identity: (parsed, mapped) => ({
    fullName: parsed.fullName,
    title: parsed.title || undefined,
    seniority: (parsed.seniority as CandidateSeniority) || undefined,
    employerName: mapped ? undefined : parsed.employerName || undefined,
    locationCity: parsed.locationCity || undefined,
    locationCountry: parsed.locationCountry || undefined,
  }),
  summary: (parsed) => ({ summary: parsed.summary || undefined }),
  experience: (parsed) => ({
    career: parsed.career
      .filter((entry) => entry.company || entry.title || entry.period)
      .map((entry) => ({
        company: entry.company || null,
        title: entry.title || null,
        period: entry.period || null,
      })),
  }),
  compensation: (parsed) => ({
    compensation: {
      currency: parsed.currency || null,
      baseSalary: parsed.baseSalary ?? null,
      bonus: parsed.bonus ?? null,
      allowances: parsed.allowances ?? null,
      longTermIncentive: parsed.longTermIncentive ?? null,
      noticePeriod: parsed.noticePeriod || null,
    },
  }),
  background: (parsed) => ({
    nationality: parsed.nationality || undefined,
    yearsExperience: parsed.yearsExperience,
    languages: parsed.languages
      .split(",")
      .map((language) => language.trim())
      .filter(Boolean),
  }),
  contact: (parsed) => ({
    email: parsed.email || undefined,
    phone: parsed.phone || undefined,
    linkedinUrl: parsed.linkedinUrl || undefined,
  }),
  note: (parsed) => ({ note: parsed.note || undefined }),
};

export function patchOf<S extends ProfileFormSection>(
  section: S,
  parsed: SectionValues<S>,
  mapped: boolean,
): Partial<SaveCandidatePayload> {
  return PATCHES[section](parsed, mapped);
}

/**
 * The whole form as the request that adds an executive: every section's patch at once, plus the
 * status and the company link, which no section owns. The company link is where the form was opened
 * from; without one the executive lands unmapped, with the employer they were typed with.
 */
export function payloadOf(
  parsed: ParsedCandidateForm,
  triageCompanyId: string | null,
): SaveCandidatePayload {
  const mapped = triageCompanyId !== null;
  return {
    triageCompanyId,
    status: (parsed.status as CandidateStatus) || undefined,
    ...PATCHES.identity(parsed, mapped),
    ...PATCHES.summary(parsed, mapped),
    ...PATCHES.experience(parsed, mapped),
    ...PATCHES.compensation(parsed, mapped),
    ...PATCHES.background(parsed, mapped),
    ...PATCHES.contact(parsed, mapped),
    ...PATCHES.note(parsed, mapped),
    fullName: parsed.fullName,
  };
}
