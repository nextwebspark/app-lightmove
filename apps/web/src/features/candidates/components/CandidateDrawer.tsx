import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { z } from "zod";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, Select, TextArea, useToast } from "../../../components/ui";
import { Drawer } from "../../../components/ui/Drawer";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { optionalNumber, optionalWebAddress } from "../../../lib/formFields";
import * as candidatesApi from "../api/candidatesApi";
import type {
  Candidate,
  CandidateSeniority,
  CandidateStatus,
  SaveCandidatePayload,
} from "../api/types";
import { CANDIDATE_SENIORITIES, CANDIDATE_STATUSES } from "../lib/candidateVocabulary";

/**
 * Only the name is required, matching the server. Research arrives in pieces — a name, a company and
 * a rough title from a conference — and demanding a complete profile would send that name into a
 * spreadsheet, which is what these screens exist to replace.
 */
const candidateSchema = z.object({
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
  languages: z.string().trim().max(400),
  currency: z.string().trim().max(3),
  baseSalary: optionalNumber("Base salary", Number.MAX_SAFE_INTEGER),
  bonus: optionalNumber("Bonus", Number.MAX_SAFE_INTEGER),
  allowances: optionalNumber("Allowances", Number.MAX_SAFE_INTEGER),
  longTermIncentive: optionalNumber("Long-term incentive", Number.MAX_SAFE_INTEGER),
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

/** What the inputs hold: every scalar a string, because that is what a text input gives back. */
type CandidateForm = z.input<typeof candidateSchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
type ParsedCandidateForm = z.output<typeof candidateSchema>;

const EMPTY_FORM: CandidateForm = {
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

/** The company a new executive is being added at, if the drawer was opened from a company's row. */
export interface CandidateCompanyContext {
  triageCompanyId: string;
  companyName: string;
}

/**
 * Adds or edits one executive.
 *
 * <p>A slide-over rather than a modal, deliberately: this is the widest form in the product and the
 * grid behind it is the context — a researcher checks which company's row they opened while typing.
 *
 * <p><b>Editing is a full replace</b>, which is why every field is on screen at once rather than
 * behind progressive disclosure. The server takes what this submits as the whole profile, so a field
 * the drawer does not show is a field the next save would silently clear.
 *
 * <p>Where the drawer was opened from a company's row the employer is that company and the field is
 * read-only: the mapping and the name must not be able to disagree, and the server ignores a typed
 * employer in that case anyway. Opened from the toolbar there is no company, the field is free text,
 * and the row lands unmapped — the executive whose employer is not in the mandate's universe.
 */
export function CandidateDrawer({
  open,
  projectId,
  candidate,
  company,
  onClose,
  onSaved,
  onDelete,
}: {
  open: boolean;
  projectId: string;
  /** The executive being edited, or null to add a new one. */
  candidate: Candidate | null;
  /** The company this executive sits at, when the drawer was opened from that company's row. */
  company: CandidateCompanyContext | null;
  onClose: () => void;
  onSaved: () => void;
  /** Opens the confirmation. Absent while adding — there is nothing to remove yet. */
  onDelete?: (candidate: Candidate) => void;
}) {
  const toast = useToast();
  // State rather than a read of `save.isError`, which outlives the thing it described: a duplicate
  // name marks the name field, and the moment the user edits that name the field error clears while
  // the mutation stays failed — so the same sentence reappeared over a form already corrected.
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
    // Three parameters, not one: the fields hold strings and the schema hands back numbers, so the
    // form's input and output types genuinely differ and `handleSubmit` receives the parsed shape.
  } = useForm<CandidateForm, unknown, ParsedCandidateForm>({
    resolver: zodResolver(candidateSchema),
    defaultValues: EMPTY_FORM,
  });

  const career = useFieldArray({ control, name: "career" });

  const save = useMutation({
    mutationFn: (parsed: ParsedCandidateForm) => {
      const payload = payloadOf(parsed, company, candidate);
      return candidate
        ? candidatesApi.updateCandidate(projectId, candidate.id, payload)
        : candidatesApi.createCandidate(projectId, payload);
    },
    onSuccess: (saved) => {
      onSaved();
      toast(candidate ? `${saved.fullName} updated` : `${saved.fullName} added`);
      onClose();
    },
    onError: (error) => {
      // A name the mandate already maps belongs on the name field, because that is the field to
      // change. Anything else — a refused write, a dropped connection — is not about the name.
      if (codeOf(error) === "CANDIDATE_ALREADY_MAPPED") {
        setError("fullName", { message: messageFor(error) });
        return;
      }
      setSubmitError(messageFor(error));
    },
  });

  // Reopening should show the executive being edited, or a blank form — never the half-typed profile
  // that was abandoned, and never the last attempt's error above fields it was not about.
  useEffect(() => {
    if (!open) return;
    reset(candidate ? formOf(candidate) : { ...EMPTY_FORM, employerName: company?.companyName ?? "" });
    setSubmitError(null);
  }, [open, candidate, company, reset]);

  const employerLabel = company ? company.companyName : undefined;

  return (
    <Drawer open={open} onClose={onClose} wide label={candidate ? "Edit executive" : "Add executive"}>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute end-3.5 top-3.5 rounded-md p-1.5 text-text3 transition hover:bg-panel2 hover:text-text"
        >
          <Icon d={ICONS.close} size={16} />
        </button>
        <h2 className="font-sans text-base font-semibold">
          {candidate ? "Edit executive" : "Add executive"}
        </h2>
        <p className="mt-1 pe-8 font-mono text-[11.5px] text-text3">
          {employerLabel
            ? `At ${employerLabel}`
            : "Not tied to a company in this mandate's universe — name their employer below."}
        </p>
      </div>

      <form
        onSubmit={handleSubmit((parsed) => {
          // Last attempt's banner clears on the next one, so it never outlives what it described.
          setSubmitError(null);
          save.mutate(parsed);
        })}
        noValidate
        className="flex min-h-0 flex-1 flex-col"
      >
        <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
          <FormError message={submitError} />

          <Section title="Identity">
            <Field label="Full name" error={errors.fullName?.message}>
              <Input
                {...register("fullName")}
                autoFocus
                placeholder="Yasmin El-Sayed"
                invalid={Boolean(errors.fullName)}
              />
            </Field>
            <div className="grid gap-x-4 sm:grid-cols-2">
              <Field label="Title" error={errors.title?.message}>
                <Input {...register("title")} placeholder="VP Finance" />
              </Field>
              <Field label="Employer" error={errors.employerName?.message}>
                <Input
                  {...register("employerName")}
                  readOnly={Boolean(company)}
                  placeholder="Al Rawabi Dairy"
                  className={company ? "text-text3" : undefined}
                />
              </Field>
              <Field label="Seniority" error={errors.seniority?.message}>
                <Select {...register("seniority")}>
                  <option value="">Not established</option>
                  {CANDIDATE_SENIORITIES.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Status" error={errors.status?.message}>
                <Select {...register("status")}>
                  {CANDIDATE_STATUSES.map((status) => (
                    <option key={status.value} value={status.value}>
                      {status.label}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          </Section>

          <Section title="Contact">
            <div className="grid gap-x-4 sm:grid-cols-2">
              <Field label="Email" error={errors.email?.message}>
                <Input
                  {...register("email")}
                  inputMode="email"
                  placeholder="yasmin@example.com"
                  invalid={Boolean(errors.email)}
                />
              </Field>
              <Field label="Phone" error={errors.phone?.message}>
                <Input {...register("phone")} inputMode="tel" placeholder="+971 50 000 0000" />
              </Field>
            </div>
            <Field label="LinkedIn" error={errors.linkedinUrl?.message}>
              <Input
                {...register("linkedinUrl")}
                placeholder="linkedin.com/in/…"
                invalid={Boolean(errors.linkedinUrl)}
              />
            </Field>
          </Section>

          <Section title="Location">
            <div className="grid gap-x-4 sm:grid-cols-2">
              <Field label="Country" error={errors.locationCountry?.message}>
                <Input {...register("locationCountry")} placeholder="United Arab Emirates" />
              </Field>
              <Field label="City" error={errors.locationCity?.message}>
                <Input {...register("locationCity")} placeholder="Dubai" />
              </Field>
            </div>
            <Field
              label="Nationality"
              hint="Not the same fact as country — visa status and local credibility follow it."
              error={errors.nationality?.message}
            >
              <Input {...register("nationality")} placeholder="Egyptian" />
            </Field>
          </Section>

          <Section title="Experience">
            <div className="grid gap-x-4 sm:grid-cols-2">
              <Field label="Years of experience" error={errors.yearsExperience?.message}>
                <Input {...register("yearsExperience")} inputMode="numeric" placeholder="18" />
              </Field>
              <Field
                label="Languages"
                hint="Comma separated."
                error={errors.languages?.message}
              >
                <Input {...register("languages")} placeholder="English, Arabic" />
              </Field>
            </div>
            <Field label="Profile summary" error={errors.summary?.message}>
              <TextArea
                {...register("summary")}
                rows={3}
                placeholder="A decade in regional FMCG finance leadership…"
              />
            </Field>

            <div className="mb-4">
              <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
                Career history
              </div>
              {career.fields.map((row, index) => (
                <div key={row.id} className="mb-2 flex items-start gap-2">
                  <Input
                    {...register(`career.${index}.company`)}
                    placeholder="Company"
                    aria-label={`Career ${index + 1} company`}
                    className="flex-1"
                  />
                  <Input
                    {...register(`career.${index}.title`)}
                    placeholder="Title"
                    aria-label={`Career ${index + 1} title`}
                    className="flex-1"
                  />
                  <Input
                    {...register(`career.${index}.period`)}
                    placeholder="2021–Present"
                    aria-label={`Career ${index + 1} period`}
                    className="w-[110px] flex-none"
                  />
                  <button
                    type="button"
                    onClick={() => career.remove(index)}
                    aria-label={`Remove career row ${index + 1}`}
                    className="mt-2 flex-none rounded-md p-1.5 text-text3 transition hover:bg-panel2 hover:text-red"
                  >
                    <Icon d={ICONS.trash} size={14} />
                  </button>
                </div>
              ))}
              <button
                type="button"
                onClick={() => career.append({ company: "", title: "", period: "" })}
                className="inline-flex items-center gap-1.5 rounded-[6px] border border-dashed border-line px-3 py-2 font-sans text-[13px] text-text2 transition hover:border-text3 hover:text-text"
              >
                <Icon d={ICONS.plus} size={14} />
                Add a post
              </button>
              {errors.career?.message && (
                <span role="alert" className="mt-1.5 block font-mono text-[11px] text-red">
                  {errors.career.message}
                </span>
              )}
            </div>
          </Section>

          <Section title="Compensation">
            <p className="mb-3 font-mono text-[11px] text-text3">
              Whole units, in the currency it was quoted in. Nothing converts it.
            </p>
            <div className="grid gap-x-4 sm:grid-cols-2">
              <Field label="Currency" error={errors.currency?.message}>
                <Input {...register("currency")} placeholder="AED" maxLength={3} />
              </Field>
              <Field label="Notice period" error={errors.noticePeriod?.message}>
                <Input {...register("noticePeriod")} placeholder="3 months" />
              </Field>
              <Field label="Base" error={errors.baseSalary?.message}>
                <Input {...register("baseSalary")} inputMode="numeric" placeholder="420000" />
              </Field>
              <Field label="Bonus" error={errors.bonus?.message}>
                <Input {...register("bonus")} inputMode="numeric" placeholder="80000" />
              </Field>
              <Field label="Allowances" error={errors.allowances?.message}>
                <Input {...register("allowances")} inputMode="numeric" placeholder="40000" />
              </Field>
              <Field label="Long-term incentive" error={errors.longTermIncentive?.message}>
                <Input {...register("longTermIncentive")} inputMode="numeric" placeholder="0" />
              </Field>
            </div>
          </Section>

          <Section title="Notes">
            <Field
              label="Note"
              hint="Your own remark on this person, for this mandate."
              error={errors.note?.message}
            >
              <TextArea {...register("note")} rows={3} placeholder="Met at a conference last year…" />
            </Field>
          </Section>
        </div>

        <div className="flex flex-none items-center gap-2 border-t border-line-soft px-5 py-3">
          {candidate && onDelete && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => onDelete(candidate)}
              className="text-red"
            >
              Remove
            </Button>
          )}
          <div className="ms-auto flex gap-2">
            <Button type="button" variant="secondary" onClick={onClose} disabled={save.isPending}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={save.isPending}>
              {candidate ? "Save changes" : "Add executive"}
            </Button>
          </div>
        </div>
      </form>
    </Drawer>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mb-2 border-b border-line-soft pb-2 last:border-b-0">
      <h3 className="mb-3 font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-text3">
        {title}
      </h3>
      {children}
    </section>
  );
}

/** The stored profile, back in the shapes the inputs hold: strings, and a comma-joined language list. */
function formOf(candidate: Candidate): CandidateForm {
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
    baseSalary: candidate.compensation.baseSalary?.toString() ?? "",
    bonus: candidate.compensation.bonus?.toString() ?? "",
    allowances: candidate.compensation.allowances?.toString() ?? "",
    longTermIncentive: candidate.compensation.longTermIncentive?.toString() ?? "",
    noticePeriod: candidate.compensation.noticePeriod ?? "",
    career: candidate.career.map((entry) => ({
      company: entry.company ?? "",
      title: entry.title ?? "",
      period: entry.period ?? "",
    })),
  };
}

/**
 * The form, as the server takes it. Empty strings become omissions rather than blanks — the server
 * treats a blank as null anyway, and sending `""` would make the network log lie about what was typed.
 *
 * <p>The company link comes from where the drawer was opened, falling back to where the executive is
 * already mapped: editing someone from their own row must not quietly unmap them.
 */
function payloadOf(
  parsed: ParsedCandidateForm,
  company: CandidateCompanyContext | null,
  editing: Candidate | null,
): SaveCandidatePayload {
  const triageCompanyId = company?.triageCompanyId ?? editing?.triageCompanyId ?? null;
  return {
    triageCompanyId,
    fullName: parsed.fullName,
    title: parsed.title || undefined,
    seniority: (parsed.seniority as CandidateSeniority) || undefined,
    status: (parsed.status as CandidateStatus) || undefined,
    employerName: triageCompanyId ? undefined : parsed.employerName || undefined,
    email: parsed.email || undefined,
    phone: parsed.phone || undefined,
    linkedinUrl: parsed.linkedinUrl || undefined,
    locationCountry: parsed.locationCountry || undefined,
    locationCity: parsed.locationCity || undefined,
    nationality: parsed.nationality || undefined,
    yearsExperience: parsed.yearsExperience,
    summary: parsed.summary || undefined,
    note: parsed.note || undefined,
    compensation: {
      currency: parsed.currency || null,
      baseSalary: parsed.baseSalary ?? null,
      bonus: parsed.bonus ?? null,
      allowances: parsed.allowances ?? null,
      longTermIncentive: parsed.longTermIncentive ?? null,
      noticePeriod: parsed.noticePeriod || null,
    },
    career: parsed.career
      .filter((entry) => entry.company || entry.title || entry.period)
      .map((entry) => ({
        company: entry.company || null,
        title: entry.title || null,
        period: entry.period || null,
      })),
    languages: parsed.languages
      .split(",")
      .map((language) => language.trim())
      .filter(Boolean),
  };
}
