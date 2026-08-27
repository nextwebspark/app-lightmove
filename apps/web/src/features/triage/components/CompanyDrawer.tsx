import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, TextArea, useToast } from "../../../components/ui";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import {
  DetailGrid,
  DetailPill,
  DetailTile,
  DrawerSection,
} from "../../../components/ui/DetailList";
import { Drawer } from "../../../components/ui/Drawer";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { formatInstantDate, formatMoney } from "../../../lib/format";
import { optionalNumber, optionalWebAddress } from "../../../lib/formFields";
import * as triageApi from "../api/triageApi";
import type {
  CaptureCompanyPayload,
  EditCompanyPayload,
  TriageCompany,
  TriageCompanyStatus,
} from "../api/types";
import { stageByStatus } from "../lib/triageStages";
import { MOVES, SOURCE_STYLES } from "../lib/triageVocabulary";

/**
 * Only the name is required, matching the server. The plugin reads whatever a page publishes and a
 * researcher may have a name and a country and nothing else; demanding a complete record would send
 * the consultant back to the spreadsheet these screens exist to replace.
 */
const companySchema = z.object({
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
type CompanyForm = z.input<typeof companySchema>;

/** What the schema hands back once parsed — the numeric fields coerced, or absent. */
type ParsedCompanyForm = z.output<typeof companySchema>;

const EMPTY_FORM: CompanyForm = {
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

/**
 * One company, in the mandate's own terms — the panel the mockup's `coDrawer` describes, finally
 * built, and the form that adds a new one.
 *
 * <p><b>It opens read-only.</b> A consultant clicking a company name is reading, not typing, and a
 * form that opens over the grid on every click is a form you dismiss without looking at. Edit is a
 * deliberate second step.
 *
 * <p><b>A company taken from Strategy has no Edit button at all.</b> Its fields are the market
 * export's snapshot, refreshed by the export, and a mandate rewriting them would make the Source badge
 * a claim the figures no longer support. The server refuses the write too — the missing button is the
 * courtesy, not the control.
 *
 * <p>The <b>Note</b> is the exception, and stays editable on every company including those. It is the
 * mandate's own remark rather than a fact about the company, which is exactly why the export cannot
 * own it — and it is the reason a consultant opens this panel rather than Strategy's.
 */
export function CompanyDrawer({
  open,
  projectId,
  company,
  landingStatus,
  canWrite,
  onClose,
  onSaved,
  onMove,
  onDelete,
}: {
  open: boolean;
  projectId: string;
  /** The company being read, or null to add a new one. */
  company: TriageCompany | null;
  /** Where a newly captured company lands — the stage the grid was showing. */
  landingStatus: TriageCompanyStatus;
  canWrite: boolean;
  onClose: () => void;
  onSaved: () => void;
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [note, setNote] = useState("");
  // State rather than a read of `save.isError`, which outlives the thing it described: a duplicate
  // name marks the name field, and the moment the user edits that name the field error clears while
  // the mutation stays failed — so the same sentence reappeared over a form already corrected.
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
    // Three parameters, not one: the fields hold strings and the schema hands back numbers, so the
    // form's input and output types genuinely differ and `handleSubmit` receives the parsed shape.
  } = useForm<CompanyForm, unknown, ParsedCompanyForm>({
    resolver: zodResolver(companySchema),
    defaultValues: EMPTY_FORM,
  });

  const save = useMutation({
    mutationFn: (parsed: ParsedCompanyForm) =>
      company
        ? triageApi.editTriageCompany(projectId, company.id, editPayloadOf(parsed))
        : triageApi.captureCompany(projectId, capturePayloadOf(parsed, landingStatus)),
    onSuccess: (saved) => {
      onSaved();
      toast(company ? `${saved.companyName} updated` : `${saved.companyName} added`);
      if (company) setEditing(false);
      else onClose();
    },
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

  const saveNote = useMutation({
    mutationFn: (text: string) =>
      triageApi.updateTriageCompany(projectId, company!.id, { note: text }),
    onSuccess: () => {
      onSaved();
      toast("Note saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  // Reopening should show the company being read, or a blank form — never the half-typed capture that
  // was abandoned, and never the last attempt's error above fields it was not about.
  useEffect(() => {
    if (!open) return;
    reset(company ? formOf(company) : EMPTY_FORM);
    setNote(company?.note ?? "");
    setEditing(company === null);
    setSubmitError(null);
  }, [open, company, reset]);

  // A company the mandate supplied itself is the mandate's to rewrite; one taken from the market is
  // the export's. Keyed on `source` and not on `apolloAccountId`, because that is the test the server
  // applies — and the two can disagree, which would put an Edit button on a row the API then refuses.
  //
  // Named for the invariant rather than for what this screen concludes from it, and named identically
  // to `TriageCompany.isMandateSupplied` on the server: one rule with two names across the wire is how
  // the apolloAccountId/source split above happened in the first place. Editing is that rule plus a
  // permission, which is what `canEdit` says.
  const isMandateSupplied = company !== null && company.source !== "strategy";
  const canEdit = isMandateSupplied && canWrite;
  const label = company ? company.companyName : "Add a company";

  return (
    <Drawer open={open} onClose={onClose} wide label={label}>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute end-3.5 top-3.5 rounded-md p-1.5 text-text3 transition hover:bg-panel2 hover:text-text"
        >
          <Icon d={ICONS.close} size={16} />
        </button>

        {company ? (
          <div className="flex items-start gap-3 pe-8">
            <CompanyLogo name={company.companyName} logo={company.logoUrl} size={44} />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-sans text-base font-semibold">{company.companyName}</h2>
                <DetailPill
                  label={SOURCE_STYLES[company.source].label}
                  className={SOURCE_STYLES[company.source].className}
                />
                <DetailPill label={stageByStatus(company.status).label} />
              </div>
              <p className="mt-1 font-mono text-[11.5px] text-text3">
                {[company.industry, company.companyCity, company.companyCountry]
                  .filter(Boolean)
                  .join(" · ") || "Nothing recorded about where it sits"}
              </p>
            </div>
            {canEdit && !editing && (
              <Button type="button" variant="secondary" onClick={() => setEditing(true)}>
                <Icon d={ICONS.pencil} size={14} />
                Edit
              </Button>
            )}
          </div>
        ) : (
          <>
            <h2 className="font-sans text-base font-semibold">Add a company</h2>
            <p className="mt-1 pe-8 font-mono text-[11.5px] text-text3">
              For a company the market export does not carry. Only the name is required.
            </p>
          </>
        )}
      </div>

      {editing ? (
        <CompanyForm
          errors={errors}
          register={register}
          submitError={submitError}
          saving={save.isPending}
          isCapture={company === null}
          onCancel={() => (company ? setEditing(false) : onClose())}
          onSubmit={handleSubmit((parsed) => {
            // Last attempt's banner clears on the next one, so it never outlives what it described.
            setSubmitError(null);
            save.mutate(parsed);
          })}
        />
      ) : (
        company && (
          <>
            <div className="min-h-0 flex-1 overflow-y-auto px-5">
              <DrawerSection title="About">
                <p className="text-[13px]/[1.6] text-text2">
                  {company.shortDescription ?? "No description was captured for this company."}
                </p>
              </DrawerSection>

              <DrawerSection title="Scale snapshot">
                <DetailGrid>
                  <DetailTile label="Revenue" value={formatMoney(company.annualRevenue)} />
                  <DetailTile
                    label="Employees"
                    value={company.numEmployees?.toLocaleString() ?? null}
                  />
                  <DetailTile label="Founded" value={company.foundedYear?.toString() ?? null} />
                  <DetailTile label="Sector" value={company.industry} />
                  <DetailTile
                    label="Links"
                    full
                    value={
                      company.website || company.companyLinkedinUrl ? (
                        <span className="flex gap-1">
                          <CompanyLink
                            url={company.website}
                            icon={ICONS.globe}
                            label="website"
                            companyName={company.companyName}
                          />
                          <CompanyLink
                            url={company.companyLinkedinUrl}
                            icon={ICONS.linkedin}
                            label="LinkedIn"
                            companyName={company.companyName}
                          />
                        </span>
                      ) : null
                    }
                  />
                </DetailGrid>
              </DrawerSection>

              <DrawerSection title="In this mandate">
                <DetailGrid>
                  <DetailTile label="Stage" value={stageByStatus(company.status).label} />
                  <DetailTile label="Source" value={SOURCE_STYLES[company.source].label} />
                  <DetailTile label="Added" value={formatInstantDate(company.addedAt)} />
                  <DetailTile label="Country" value={company.companyCountry} />
                </DetailGrid>
                {!isMandateSupplied && (
                  <p className="mt-3 font-mono text-[11px]/[1.6] text-text3">
                    These fields come from the market export and are refreshed by it, so they are not
                    editable here. Your note below is yours.
                  </p>
                )}
              </DrawerSection>

              <DrawerSection
                title="Note"
                action={
                  canWrite &&
                  note !== (company.note ?? "") && (
                    <button
                      type="button"
                      onClick={() => saveNote.mutate(note)}
                      disabled={saveNote.isPending}
                      className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-amber transition hover:underline disabled:opacity-50"
                    >
                      Save
                    </button>
                  )
                }
              >
                <TextArea
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  readOnly={!canWrite}
                  rows={3}
                  aria-label="Note on this company"
                  placeholder="Your own remark on this company, for this mandate…"
                />
              </DrawerSection>
            </div>

            {canWrite && (
              <div className="flex flex-none flex-wrap items-center gap-2 border-t border-line-soft px-5 py-3">
                {MOVES[company.status].map((move) => (
                  <Button
                    key={move.status}
                    type="button"
                    variant="secondary"
                    onClick={() => onMove(company, move.status)}
                  >
                    <Icon d={move.icon} size={14} />
                    {move.label}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="secondary"
                  className="ms-auto text-red"
                  onClick={() => onDelete(company)}
                >
                  Remove
                </Button>
              </div>
            )}
          </>
        )
      )}
    </Drawer>
  );
}

function CompanyForm({
  errors,
  register,
  submitError,
  saving,
  isCapture,
  onCancel,
  onSubmit,
}: {
  errors: ReturnType<typeof useForm<CompanyForm, unknown, ParsedCompanyForm>>["formState"]["errors"];
  register: ReturnType<typeof useForm<CompanyForm, unknown, ParsedCompanyForm>>["register"];
  submitError: string | null;
  saving: boolean;
  /** A capture collects the first note; an edit leaves the note to its own inline save. */
  isCapture: boolean;
  onCancel: () => void;
  onSubmit: (event: React.FormEvent) => void;
}) {
  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
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
        <Button type="button" variant="secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={saving}>
          {isCapture ? "Add company" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}

/** The stored snapshot, back in the shapes the inputs hold: strings, and an absent number as "". */
function formOf(company: TriageCompany): CompanyForm {
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

function editPayloadOf(parsed: ParsedCompanyForm): EditCompanyPayload {
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

function capturePayloadOf(
  parsed: ParsedCompanyForm,
  landingStatus: TriageCompanyStatus,
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
