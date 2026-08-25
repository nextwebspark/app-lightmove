import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Input, Modal, TextArea, useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as triageApi from "../api/triageApi";
import type { CaptureCompanyPayload, TriageCompanyStatus } from "../api/types";

/**
 * A number typed into a text input arrives as a string, and an untouched one as `""`. Coercing
 * blindly would send `NaN`, and `z.coerce.number()` reads `""` as 0 — a company with no published
 * headcount would be filed as having none, which is a different claim. So: empty means omitted.
 */
const optionalNumber = (label: string, max: number) =>
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
 * Only the name is required, matching the server. The plugin reads whatever a page publishes and a
 * researcher may have a name and a country and nothing else; demanding a complete record would send
 * the consultant back to the spreadsheet these screens exist to replace.
 */
const captureSchema = z.object({
  companyName: z.string().trim().min(1, "A company name is required").max(200),
  website: z.string().trim().max(500),
  companyLinkedinUrl: z.string().trim().max(500),
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
  note: z.string().trim().max(2000),
});

/** What the inputs hold: every field a string, because that is what a text input gives back. */
type CaptureForm = z.input<typeof captureSchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
type CapturedForm = z.output<typeof captureSchema>;

const EMPTY_FORM: CaptureForm = {
  companyName: "",
  website: "",
  companyLinkedinUrl: "",
  industry: "",
  companyCountry: "",
  companyCity: "",
  numEmployees: "",
  annualRevenue: "",
  foundedYear: "",
  note: "",
};

/**
 * Adds a company the market does not carry.
 *
 * <p>The fields mirror the browser plugin's company capture in `Extension.dc.html`, deliberately: the
 * plugin and this form write the same record through the same endpoint, so a company captured off a
 * LinkedIn page and one typed in here are the same kind of row and differ only in their source.
 *
 * <p>It lands in whichever stage the page was on. Adding a company while looking at the shortlist
 * means shortlisting it — bouncing it to the universe and making the consultant move it back would
 * be the screen ignoring what it was just told.
 */
export function AddCompanyModal({
  open,
  projectId,
  landingStatus,
  onClose,
  onAdded,
}: {
  open: boolean;
  projectId: string;
  landingStatus: TriageCompanyStatus;
  onClose: () => void;
  onAdded: () => void;
}) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
    // Three parameters, not one: the fields hold strings and the schema hands back numbers, so the
    // form's input and output types genuinely differ and `handleSubmit` receives the parsed shape.
  } = useForm<CaptureForm, unknown, CapturedForm>({
    resolver: zodResolver(captureSchema),
    defaultValues: EMPTY_FORM,
  });

  const capture = useMutation({
    mutationFn: (parsed: CapturedForm) => {
      const payload: CaptureCompanyPayload = {
        companyName: parsed.companyName,
        source: "manual",
        status: landingStatus,
        website: parsed.website || undefined,
        companyLinkedinUrl: parsed.companyLinkedinUrl || undefined,
        industry: parsed.industry || undefined,
        companyCountry: parsed.companyCountry || undefined,
        companyCity: parsed.companyCity || undefined,
        numEmployees: parsed.numEmployees,
        annualRevenue: parsed.annualRevenue,
        foundedYear: parsed.foundedYear,
        note: parsed.note || undefined,
      };
      return triageApi.captureCompany(projectId, payload);
    },
    onSuccess: (company) => {
      onAdded();
      toast(`${company.companyName} added`);
      onClose();
    },
    onError: (error) => {
      // A name the mandate already holds belongs on the name field, because that is the field to
      // change. Anything else — a refused write, a dropped connection — is not about the name, and
      // marking it there would send the user editing a company name that was never the problem.
      if (codeOf(error) === "TRIAGE_COMPANY_ALREADY_HELD") {
        setError("companyName", { message: messageFor(error) });
      }
    },
  });

  // Reopening after a cancel should be a blank form, not the half-typed company that was abandoned.
  // The mutation is reset with it: its error outlives the fields it was about, so a failed capture
  // left "this mandate already holds a company with that name" sitting above an empty form.
  useEffect(() => {
    if (open) {
      reset(EMPTY_FORM);
      capture.reset();
    }
  }, [open, reset, capture.reset]);

  return (
    <Modal open={open} onClose={onClose} title="Add a company" className="md:w-[560px]">
      <p className="mb-4 text-[13px]/[1.6] text-text2">
        For a company the market export does not carry. Only the name is required — fill in whatever
        you have.
      </p>

      <FormError
        message={capture.isError && !errors.companyName ? messageFor(capture.error) : null}
      />

      <form onSubmit={handleSubmit((parsed) => capture.mutate(parsed))} noValidate>
        <Field label="Company name" error={errors.companyName?.message}>
          <Input
            {...register("companyName")}
            autoFocus
            placeholder="Gulf Industrial Holdings"
            invalid={Boolean(errors.companyName)}
          />
        </Field>

        <div className="grid gap-x-4 md:grid-cols-2">
          <Field label="Website" error={errors.website?.message}>
            <Input {...register("website")} placeholder="gulfindustrial.com" />
          </Field>
          <Field label="LinkedIn" error={errors.companyLinkedinUrl?.message}>
            <Input
              {...register("companyLinkedinUrl")}
              placeholder="linkedin.com/company/…"
            />
          </Field>
          <Field label="Sector" error={errors.industry?.message}>
            <Input {...register("industry")} placeholder="industrial manufacturing" />
          </Field>
          <Field label="Country" error={errors.companyCountry?.message}>
            <Input {...register("companyCountry")} placeholder="United Arab Emirates" />
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

        <Field
          label="Note"
          hint="Your own remark on this company, for this mandate."
          error={errors.note?.message}
        >
          <TextArea {...register("note")} rows={3} placeholder="Why this one is worth a look…" />
        </Field>

        <div className="mt-1 flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={onClose}
            disabled={capture.isPending}
          >
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={capture.isPending}>
            Add company
          </Button>
        </div>
      </form>
    </Modal>
  );
}
