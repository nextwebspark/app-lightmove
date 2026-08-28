import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Controller, useForm, type Control } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Input, TextArea } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { optionalNumber, optionalWebAddress } from "../../../lib/formFields";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { FacetCount } from "../../strategy/api/types";
import { FacetCombobox } from "../../strategy/components/FacetCombobox";
import type { CaptureCompanyPayload, EditCompanyPayload, TriageCompany } from "../api/types";

/**
 * Only the name is required, matching the server. The plugin reads whatever a page publishes and a
 * researcher may have a name and a country and nothing else; demanding a complete record would send
 * the consultant back to the spreadsheet these screens exist to replace.
 */
const companyFactsSchema = z.object({
  companyName: z.string().trim().min(1, "A company name is required").max(200),
  website: optionalWebAddress("website"),
  companyLinkedinUrl: optionalWebAddress("LinkedIn URL"),
  industry: z.string().trim().max(200),
  companyCountry: z.string().trim().max(100),
  companyCity: z.string().trim().max(100),
  numEmployees: optionalNumber("Employees", 10_000_000),
  annualRevenue: optionalNumber("Revenue", Number.MAX_SAFE_INTEGER),
  foundedYear: z
    .string()
    .trim()
    .transform((value) => (value === "" ? undefined : Number(value)))
    .refine((value) => value === undefined || (Number.isInteger(value) && value >= 1800 && value <= 2100), {
      message: "That founding year looks like a typo",
    }),
  shortDescription: z.string().trim().max(2000),
  /** Only sent on a capture: an edit leaves the note to its own write. */
  note: z.string().trim().max(2000),
});

/** What the inputs hold: every field a string, because that is what a text input gives back. */
type CompanyFactsValues = z.input<typeof companyFactsSchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
export type ParsedCompanyFacts = z.output<typeof companyFactsSchema>;

const EMPTY_FACTS: CompanyFactsValues = {
  companyName: "",
  website: "",
  companyLinkedinUrl: "",
  industry: "",
  companyCountry: "",
  companyCity: "",
  numEmployees: "",
  annualRevenue: "",
  foundedYear: "",
  shortDescription: "",
  note: "",
};

/** The counts are over the whole universe and change only when the pipeline loads. */
const FACETS_STALE_MS = 10 * 60 * 1000;

/**
 * A company's own facts, as a form — the mandate's hand-typed company being added, and the same
 * company being corrected later.
 *
 * <p>Only ever for a company the mandate supplied itself. A row taken from the market carries the
 * export's snapshot, which the export refreshes and the server refuses to let a mandate rewrite; that
 * company is added by its universe id and never through this.
 *
 * <p><b>Sector and Country are the market's own vocabulary</b>, not free text: they are the industry
 * taxonomy and the country list the Strategy filter is expressed in, so a company typed in by hand
 * files under the same names as the 71,822 the filter searches. A hand-typed "industrial mfg" would
 * read the same to a person and match nothing the filter can ask for.
 */
export function CompanyFactsForm({
  company,
  seedName = "",
  isCapture,
  save,
  onSaved,
  onCancel,
}: {
  /** The company being corrected, or null when this is adding a new one. */
  company: TriageCompany | null;
  /** What was typed into the market search before giving up on it, for a capture. */
  seedName?: string;
  /** A capture collects the first note; an edit leaves the note to its own inline save. */
  isCapture: boolean;
  save: (parsed: ParsedCompanyFacts) => Promise<TriageCompany>;
  onSaved: (saved: TriageCompany) => void;
  onCancel: () => void;
}) {
  // State rather than a read of `saving.isError`, which outlives the thing it described: a duplicate
  // name marks the name field, and the moment the user edits that name the field error clears while
  // the mutation stays failed — so the same sentence reappeared over a form already corrected.
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
    // Three parameters, not one: the fields hold strings and the schema hands back numbers, so the
    // form's input and output types genuinely differ and `handleSubmit` receives the parsed shape.
  } = useForm<CompanyFactsValues, unknown, ParsedCompanyFacts>({
    resolver: zodResolver(companyFactsSchema),
    defaultValues: company ? factsOf(company) : { ...EMPTY_FACTS, companyName: seedName },
  });

  // One key and one cache entry with Strategy's filter rail, which reads the same vocabulary.
  const facets = useQuery({
    queryKey: companiesApi.FACETS_KEY,
    queryFn: companiesApi.getFacets,
    staleTime: FACETS_STALE_MS,
  });

  // Flattened and ordered exactly as the Strategy filter offers them — A to Z. The sector groups are
  // the API's way of laying the taxonomy out, not a thing anyone picks, and a box you search does not
  // need the headings a box you scroll does.
  const industries: FacetCount[] = (facets.data?.sectorGroups ?? [])
    .flatMap((group) => group.industries)
    .sort((first, second) => first.label.localeCompare(second.label));
  const countries: FacetCount[] = facets.data?.countries ?? [];

  const saving = useMutation({
    mutationFn: save,
    onSuccess: onSaved,
    onError: (error) => {
      // A name the mandate already holds belongs on the name field, because that is the field to
      // change. Anything else — a refused write, a dropped connection — is not about the name.
      if (codeOf(error) === "TRIAGE_COMPANY_ALREADY_HELD") {
        setError("companyName", { message: messageFor(error) });
        return;
      }
      setSubmitError(messageFor(error));
    },
  });

  return (
    <form
      onSubmit={handleSubmit((parsed) => {
        // Last attempt's banner clears on the next one, so it never outlives what it described.
        setSubmitError(null);
        saving.mutate(parsed);
      })}
      noValidate
      className="flex min-h-0 flex-1 flex-col"
    >
      <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
        <FormError message={submitError} />

        <Field label="Company name" error={errors.companyName?.message}>
          <Input
            {...register("companyName")}
            autoFocus
            placeholder="Gulf Industrial Holdings"
            invalid={Boolean(errors.companyName)}
          />
        </Field>

        <div className="grid gap-x-4 sm:grid-cols-2">
          <Field label="Website" error={errors.website?.message}>
            <Input {...register("website")} placeholder="gulfindustrial.com" />
          </Field>
          <Field label="LinkedIn" error={errors.companyLinkedinUrl?.message}>
            <Input {...register("companyLinkedinUrl")} placeholder="linkedin.com/company/…" />
          </Field>

          <Field label="Sector" error={errors.industry?.message}>
            <VocabularyField
              name="industry"
              control={control}
              listId="company-sector"
              noun="sectors"
              options={industries}
              unavailable={facets.isError}
              placeholder="industrial manufacturing"
            />
          </Field>
          <Field label="Country" error={errors.companyCountry?.message}>
            <VocabularyField
              name="companyCountry"
              control={control}
              listId="company-country"
              noun="countries"
              options={countries}
              unavailable={facets.isError}
              placeholder="United Arab Emirates"
            />
          </Field>

          <Field label="City" error={errors.companyCity?.message}>
            <Input {...register("companyCity")} placeholder="Dubai" />
          </Field>
          <Field label="Founded" error={errors.foundedYear?.message}>
            <Input {...register("foundedYear")} inputMode="numeric" placeholder="1998" />
          </Field>
          <Field label="Employees" error={errors.numEmployees?.message}>
            <Input {...register("numEmployees")} inputMode="numeric" placeholder="2400" />
          </Field>
          <Field label="Annual revenue (USD)" error={errors.annualRevenue?.message}>
            <Input {...register("annualRevenue")} inputMode="numeric" placeholder="500000000" />
          </Field>
        </div>

        <Field label="Description" error={errors.shortDescription?.message}>
          <TextArea
            {...register("shortDescription")}
            rows={2}
            placeholder="What the company does, in a line."
          />
        </Field>

        {isCapture && (
          <Field
            label="Note"
            hint="Your own remark on this company, for this mandate."
            error={errors.note?.message}
          >
            <TextArea {...register("note")} rows={3} placeholder="Why this one is worth a look…" />
          </Field>
        )}
      </div>

      <div className="flex flex-none justify-end gap-2 border-t border-line-soft px-5 py-3">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={saving.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={saving.isPending}>
          {isCapture ? "Add company" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}

/**
 * One field the market's vocabulary answers for, bound to the form.
 *
 * <p>A `Controller` rather than a `register`, because the picker is not a control the DOM hands a
 * value back from — and because the fallback when the facets read failed is a plain input, so both
 * shapes live behind one field name instead of two registrations that could disagree.
 *
 * <p>That fallback is deliberate: both fields are optional, and refusing to take a company at all
 * because a reference list would not load helps nobody.
 */
function VocabularyField({
  name,
  control,
  listId,
  noun,
  options,
  unavailable,
  placeholder,
}: {
  name: "industry" | "companyCountry";
  control: Control<CompanyFactsValues, unknown, ParsedCompanyFacts>;
  listId: string;
  noun: string;
  options: FacetCount[];
  unavailable: boolean;
  placeholder: string;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) =>
        unavailable ? (
          <Input {...field} placeholder={placeholder} />
        ) : (
          <FacetCombobox
            listId={listId}
            noun={noun}
            value={field.value}
            options={options}
            onChange={field.onChange}
          />
        )
      }
    />
  );
}

/** The stored snapshot, back in the shapes the inputs hold: strings, and an absent number as "". */
function factsOf(company: TriageCompany): CompanyFactsValues {
  return {
    companyName: company.companyName,
    website: company.website ?? "",
    companyLinkedinUrl: company.companyLinkedinUrl ?? "",
    industry: company.industry ?? "",
    companyCountry: company.companyCountry ?? "",
    companyCity: company.companyCity ?? "",
    numEmployees: company.numEmployees?.toString() ?? "",
    annualRevenue: company.annualRevenue?.toString() ?? "",
    foundedYear: company.foundedYear?.toString() ?? "",
    shortDescription: company.shortDescription ?? "",
    // Not carried into the edit form: the note has its own inline save on the panel, and a second
    // control writing the same field is a second chance to lose what the first one saved.
    note: "",
  };
}

export function editPayloadOf(parsed: ParsedCompanyFacts): EditCompanyPayload {
  return {
    companyName: parsed.companyName,
    website: parsed.website || undefined,
    companyLinkedinUrl: parsed.companyLinkedinUrl || undefined,
    industry: parsed.industry || undefined,
    companyCountry: parsed.companyCountry || undefined,
    companyCity: parsed.companyCity || undefined,
    numEmployees: parsed.numEmployees,
    annualRevenue: parsed.annualRevenue,
    foundedYear: parsed.foundedYear,
    shortDescription: parsed.shortDescription || undefined,
  };
}

export function capturePayloadOf(
  parsed: ParsedCompanyFacts,
  landingStatus: CaptureCompanyPayload["status"],
): CaptureCompanyPayload {
  return {
    ...editPayloadOf(parsed),
    source: "manual",
    // Adding a company while looking at the shortlist means shortlisting it — bouncing it to the
    // universe and making the consultant move it back would be the screen ignoring what it was told.
    status: landingStatus,
    note: parsed.note || undefined,
  };
}
