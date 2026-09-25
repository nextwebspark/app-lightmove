import { zodResolver } from "@hookform/resolvers/zod";
import type { Resolver } from "react-hook-form";
import { z } from "zod";
import { formatNumber } from "../../../lib/format";
import { optionalNumber, optionalWebAddress } from "../../../lib/formFields";
import {
  allowanceTotalOf,
  annualBaseOf,
  bonusAmountOf,
  bonusBasisOf,
  bonusPercentOf,
  incentiveTypesFor,
} from "./compensation";
import type {
  Candidate,
  CandidateEmail,
  CandidatePhone,
  CandidateSeniority,
  CandidateStatus,
  ContactEntryInput,
  LongTermIncentiveType,
  SaveCandidatePayload,
} from "../api/types";

const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** What one email or phone line holds while it is being edited. `source` rides along, read-only. */
export const contactEntrySchema = z.object({
  value: z.string().trim().max(320),
  kind: z.enum(["", "work", "personal"]),
  verified: z.boolean(),
  source: z.string().nullable(),
});

export type ContactEntryForm = z.input<typeof contactEntrySchema>;

/** The identity two spellings of one contact share, as the server keys the ledger. */
export function contactKeyOf(channel: "email" | "phone", value: string): string {
  const trimmed = value.trim();
  return channel === "phone" ? trimmed.replace(/\D/g, "") : trimmed.toLowerCase();
}

/**
 * A channel's lines. Blank lines are allowed and dropped on save; a line that holds something must
 * be an address (for email), and no two lines may be one contact spelled twice — the second is
 * marked, because silently merging it would hide a slip.
 */
export function contactEntries(channel: "email" | "phone") {
  return z
    .array(contactEntrySchema)
    .max(10, `Ten ${channel === "email" ? "email addresses" : "phone numbers"} is the most a profile holds`)
    .superRefine((entries, context) => {
      const seen = new Set<string>();
      entries.forEach((entry, index) => {
        const key = contactKeyOf(channel, entry.value);
        if (!key) return;
        if (channel === "email" && !EMAIL_SHAPE.test(entry.value.trim())) {
          context.addIssue({
            code: "custom",
            path: [index, "value"],
            message: "That doesn't look like a valid email",
          });
          return;
        }
        if (seen.has(key)) {
          context.addIssue({
            code: "custom",
            path: [index, "value"],
            message: `This ${channel} is already listed`,
          });
        }
        seen.add(key);
      });
    });
}

/** A channel's lines as the request states them: blanks dropped, kind and verified as claimed. */
export function contactInputsOf(entries: ContactEntryForm[]): ContactEntryInput[] {
  return entries
    .filter((entry) => entry.value.trim() !== "")
    .map((entry) => ({
      value: entry.value.trim(),
      kind: entry.kind === "" ? null : entry.kind,
      verified: entry.verified,
    }));
}

export function contactLineOf(entry: CandidateEmail | CandidatePhone): ContactEntryForm {
  return {
    value: "address" in entry ? entry.address : entry.number,
    kind: entry.kind ?? "",
    verified: entry.verified,
    source: entry.source,
  };
}

export const EMPTY_CONTACT_LINE: ContactEntryForm = { value: "", kind: "", verified: false, source: null };

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
  emails: contactEntries("email"),
  phones: contactEntries("phone"),
  linkedinUrl: optionalWebAddress("LinkedIn URL"),
  locationCountry: z.string().trim().max(100),
  locationCity: z.string().trim().max(100),
  nationality: z.string().trim().max(100),
  // "" is "not recorded", which the report counts apart from "other" — see CandidateGender.
  gender: z.enum(["", "female", "male", "other"]),
  yearsExperience: optionalNumber("Years of experience", 70),
  summary: z.string().trim().max(4000),
  note: z.string().trim().max(2000),
  // Wider than the old 400: enrichment writes this field too, and a form that refuses to save a
  // package because research listed nine languages would block the one section it was opened for.
  languages: z.string().trim().max(1200),
  currency: z.string().trim().max(3),
  baseSalary: money("Base salary"),
  // How the two figures were typed, not what is stored: the patch turns them into the annual base
  // and the bonus amount the server holds.
  baseCadence: z.enum(["annual", "monthly"]),
  bonus: bonusFigure(),
  bonusBasis: z.enum(["percent", "fixed"]),
  allowanceLines: z
    .array(z.object({ label: z.string().trim().max(60), amount: money("Allowance") }))
    .max(12, "A package lists 12 allowances at most"),
  longTermIncentive: money("Long-term incentive"),
  longTermIncentiveTypes: z.array(z.enum(["options", "rsus", "cash", "none"])),
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

/** The bonus, which alone may carry a "%": a share of base is shown as "45%". */
function bonusFigure() {
  return z
    .string()
    .transform((value) => value.replace(/[,\s%]/g, ""))
    .pipe(optionalNumber("Bonus", Number.MAX_SAFE_INTEGER));
}

/**
 * A share of base with no base comes to nothing storable, and saving it would drop a bonus that was
 * on file. Refused on the bonus field instead, in every form that edits the package.
 */
export function refineCompensation(
  values: { baseSalary?: number; bonus?: number; bonusBasis?: string },
  context: z.RefinementCtx,
) {
  if (values.bonusBasis === "percent" && values.bonus !== undefined && values.baseSalary === undefined) {
    context.addIssue({
      code: "custom",
      path: ["bonus"],
      message: "A share of base needs a base — enter one or switch to Fixed",
    });
  }
}

/** What the inputs hold: every scalar a string, because that is what a text input gives back. */
export type CandidateForm = z.input<typeof candidateSchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
export type ParsedCandidateForm = z.output<typeof candidateSchema>;

/** The three a GCC package is itemised in, offered as headings; a line left without a figure is not saved. */
const DEFAULT_ALLOWANCE_LINES: CandidateForm["allowanceLines"] = [
  { label: "Housing", amount: "" },
  { label: "Transport", amount: "" },
  { label: "Education", amount: "" },
];

export const EMPTY_FORM: CandidateForm = {
  fullName: "",
  title: "",
  seniority: "",
  status: "identified",
  employerName: "",
  emails: [],
  phones: [],
  linkedinUrl: "",
  locationCountry: "",
  locationCity: "",
  nationality: "",
  gender: "",
  yearsExperience: "",
  summary: "",
  note: "",
  languages: "",
  currency: "",
  baseSalary: "",
  baseCadence: "annual",
  bonus: "",
  bonusBasis: "percent",
  allowanceLines: DEFAULT_ALLOWANCE_LINES,
  longTermIncentive: "",
  longTermIncentiveTypes: [],
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
  compensation: [
    "currency",
    "baseSalary",
    "baseCadence",
    "bonus",
    "bonusBasis",
    "allowanceLines",
    "longTermIncentive",
    "longTermIncentiveTypes",
    "noticePeriod",
  ],
  background: ["nationality", "gender", "yearsExperience", "languages"],
  contact: ["linkedinUrl"],
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
  const picked = candidateSchema.pick(mask);
  const schema = section === "compensation" ? picked.superRefine(refineCompensation) : picked;
  return zodResolver(schema) as unknown as Resolver<
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
    emails: candidate.contacts.emails.map(contactLineOf),
    phones: candidate.contacts.phones.map(contactLineOf),
    linkedinUrl: candidate.linkedinUrl ?? "",
    locationCountry: candidate.locationCountry ?? "",
    locationCity: candidate.locationCity ?? "",
    nationality: candidate.nationality ?? "",
    gender: candidate.gender ?? "",
    yearsExperience: candidate.yearsExperience?.toString() ?? "",
    summary: candidate.summary ?? "",
    note: candidate.note ?? "",
    languages: candidate.languages.join(", "),
    currency: candidate.compensation.currency ?? "",
    ...compensationFormOf(candidate),
    longTermIncentive: amountOf(candidate.compensation.longTermIncentive),
    longTermIncentiveTypes: [...candidate.compensation.longTermIncentiveTypes],
    noticePeriod: candidate.compensation.noticePeriod ?? "",
    career: candidate.career.map((entry) => ({
      company: entry.company ?? "",
      title: entry.title ?? "",
      period: entry.period ?? "",
    })),
  };
}

/**
 * The package's figures as they reopen: the base annual, the bonus as a share of it where there is
 * one, and the allowances as their lines — or, for a total stored before lines existed, one line
 * holding it, so the figure is edited rather than silently replaced.
 */
function compensationFormOf(
  candidate: Candidate,
): Pick<CandidateForm, "baseSalary" | "baseCadence" | "bonus" | "bonusBasis" | "allowanceLines"> {
  const { baseSalary, bonus, allowances, allowanceLines } = candidate.compensation;
  const bonusBasis = bonusBasisOf(baseSalary, bonus);
  return {
    baseSalary: amountOf(baseSalary),
    baseCadence: "annual",
    bonus:
      bonusBasis === "percent" && baseSalary && bonus !== null
        ? `${bonusPercentOf(baseSalary, bonus)}%`
        : amountOf(bonus),
    bonusBasis,
    allowanceLines:
      allowanceLines.length > 0
        ? allowanceLines.map((line) => ({ label: line.label ?? "", amount: amountOf(line.amount) }))
        : allowances !== null
          ? [{ label: "Allowances", amount: amountOf(allowances) }]
          : DEFAULT_ALLOWANCE_LINES,
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
 * so a save of any other section cannot touch them. So are the contacts: the Contact section has
 * its own write, and a profile PUT never removes a contact.
 */
export function replayOf(candidate: Candidate): SaveCandidatePayload {
  return {
    triageCompanyId: candidate.triageCompanyId,
    fullName: candidate.fullName,
    title: candidate.title ?? undefined,
    seniority: candidate.seniority ?? undefined,
    status: candidate.status,
    employerName: candidate.triageCompanyId ? undefined : (candidate.companyName ?? undefined),
    linkedinUrl: candidate.linkedinUrl ?? undefined,
    locationCountry: candidate.locationCountry ?? undefined,
    locationCity: candidate.locationCity ?? undefined,
    nationality: candidate.nationality ?? undefined,
    gender: candidate.gender ?? undefined,
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
  compensation: (parsed) => {
    const baseSalary = annualBaseOf(parsed.baseSalary ?? null, parsed.baseCadence);
    // A line without a figure is a heading nobody filled in, not an allowance of nought.
    const allowanceLines = parsed.allowanceLines
      .filter((line) => line.amount !== undefined)
      .map((line) => ({ label: line.label || null, amount: line.amount ?? null }));
    return {
      compensation: {
        currency: parsed.currency || null,
        baseSalary,
        bonus: bonusAmountOf(baseSalary, parsed.bonus ?? null, parsed.bonusBasis),
        allowances: allowanceTotalOf(allowanceLines.map((line) => line.amount)),
        longTermIncentive: parsed.longTermIncentive ?? null,
        noticePeriod: parsed.noticePeriod || null,
        allowanceLines,
        longTermIncentiveTypes: incentiveTypesFor(
          parsed.longTermIncentive ?? null,
          parsed.longTermIncentiveTypes as LongTermIncentiveType[],
        ),
      },
    };
  },
  background: (parsed) => ({
    nationality: parsed.nationality || undefined,
    gender: parsed.gender || undefined,
    yearsExperience: parsed.yearsExperience,
    languages: parsed.languages
      .split(",")
      .map((language) => language.trim())
      .filter(Boolean),
  }),
  contact: (parsed) => ({
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
    emails: contactInputsOf(parsed.emails),
    phones: contactInputsOf(parsed.phones),
    fullName: parsed.fullName,
  };
}

/**
 * The Contact section on its own: the two channels and the profile link. Edited in place over the
 * read view, saved through the contacts write (and the profile PUT only when the link changed).
 */
export const contactSectionSchema = z.object({
  emails: contactEntries("email"),
  phones: contactEntries("phone"),
  linkedinUrl: optionalWebAddress("LinkedIn URL"),
});

export type ContactSectionForm = z.input<typeof contactSectionSchema>;
export type ParsedContactSectionForm = z.output<typeof contactSectionSchema>;

export function contactSectionOf(candidate: Candidate): ContactSectionForm {
  return {
    emails: candidate.contacts.emails.map(contactLineOf),
    phones: candidate.contacts.phones.map(contactLineOf),
    linkedinUrl: candidate.linkedinUrl ?? "",
  };
}
