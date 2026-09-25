import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRef, useState, type ReactNode } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../components/ui";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useSubmitShortcut } from "../../../lib/useSubmitShortcut";
import type { CustomColumn, CustomFieldValues } from "../../customcolumns/api/types";
import { CustomFieldsFieldset } from "../../customcolumns/components/CustomFieldsFieldset";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import {
  candidateSchema,
  refineCompensation,
  EMPTY_FORM,
  payloadOf,
  type CandidateForm,
  type ParsedCandidateForm,
} from "../lib/candidateForm";
import {
  BackgroundFields,
  CareerFields,
  CompensationFields,
  ContactEntriesFields,
  ContactFields,
  IdentityFields,
  NoteFields,
  StatusField,
  SummaryFields,
} from "./CandidateFieldGroups";

/** The company a new executive is being added at, if the panel was opened from a company's row. */
export interface CandidateCompanyContext {
  triageCompanyId: string;
  companyName: string;
}

/**
 * A new executive, as one form: there is nothing to read yet, so every section is open at once.
 *
 * <p>Where the panel was opened from a company's row the employer is that company and the field is
 * read-only: the mapping and the name must not be able to disagree, and the server ignores a typed
 * employer in that case anyway. Opened from the toolbar there is no company, the field is free text,
 * and the row lands unmapped — the executive whose employer is not in the mandate's universe.
 *
 * <p>Mounted fresh each time the panel opens, so a reopen never shows the half-typed profile that
 * was abandoned last time.
 */
export function AddCandidateForm({
  projectId,
  company,
  customColumns,
  defaultCurrency,
  onClose,
  onSaved,
  onMarkNoExecutiveFound,
}: {
  projectId: string;
  company: CandidateCompanyContext | null;
  /** This mandate's own person columns, filled in the same save as the fields above them. */
  customColumns: readonly CustomColumn[];
  /** The brief's currency, which a package is quoted in until somebody says otherwise. */
  defaultCurrency?: string | null;
  onClose: () => void;
  /** The created profile — the panel moves on to reading it. */
  onSaved: (saved: Candidate) => void;
  /** The research's other outcome — flags the company and closes this panel. Only offered when the
   *  panel was opened from a company's row, where there is a company to flag. */
  onMarkNoExecutiveFound?: () => void;
}) {
  const toast = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Outside react-hook-form: its schema is a fixed shape and these keys are the project's, decided at
  // runtime. They still travel in the same submit, so a save is one request and one audit event.
  const [customFields, setCustomFields] = useState<CustomFieldValues>({});

  const form = useForm<CandidateForm, unknown, ParsedCandidateForm>({
    resolver: zodResolver(candidateSchema.superRefine(refineCompensation)),
    defaultValues: {
      ...EMPTY_FORM,
      employerName: company?.companyName ?? "",
      currency: defaultCurrency ?? "",
    },
  });
  const { register, formState } = form;

  const formEl = useRef<HTMLFormElement>(null);
  const handleSubmitShortcut = useSubmitShortcut(() => formEl.current?.requestSubmit());

  const save = useMutation({
    mutationFn: (parsed: ParsedCandidateForm) =>
      candidatesApi.createCandidate(projectId, {
        ...payloadOf(parsed, company?.triageCompanyId ?? null),
        customFields,
      }),
    onSuccess: (saved) => {
      toast(`${saved.fullName} added`);
      onSaved(saved);
    },
    onError: (error) => {
      if (codeOf(error) === "CANDIDATE_ALREADY_MAPPED") {
        form.setError("fullName", { message: messageFor(error) });
        return;
      }
      setSubmitError(messageFor(error));
    },
  });

  return (
    <>
      <div className="relative flex-none border-b border-u-border px-5 py-4">
        <DrawerCloseButton onClose={onClose} />
        <h2 className="font-sans text-base font-semibold">Add executive</h2>
        <p className="mt-1 pe-8 font-mono text-[11.5px] text-u-text3">
          {company
            ? `At ${company.companyName}`
            : "Not tied to a company in this mandate's universe — name their employer below."}
        </p>
      </div>

      <FormProvider {...form}>
      <form
        ref={formEl}
        onSubmit={form.handleSubmit((parsed) => {
          setSubmitError(null);
          save.mutate(parsed);
        })}
        onKeyDown={handleSubmitShortcut}
        noValidate
        className="flex min-h-0 flex-1 flex-col"
      >
        <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
          <FormError message={submitError} />

          <Section title="Identity">
            <IdentityFields
              register={register}
              errors={formState.errors}
              control={form.control}
              employerLocked={company !== null}
              autoFocus
              statusField={<StatusField register={register} errors={formState.errors} />}
            />
          </Section>

          <Section title="Contact">
            <div className="mb-4 space-y-4">
              <div>
                <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
                  Email
                </span>
                <ContactEntriesFields channel="email" />
              </div>
              <div>
                <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
                  Phone
                </span>
                <ContactEntriesFields channel="phone" />
              </div>
            </div>
            <ContactFields />
          </Section>

          <Section title="Background">
            <BackgroundFields register={register} errors={formState.errors} />
            <SummaryFields register={register} errors={formState.errors} />
          </Section>

          <Section title="Experience">
            <CareerFields control={form.control} register={register} errors={formState.errors} />
          </Section>

          <Section title="Compensation">
            <CompensationFields
              register={register}
              errors={formState.errors}
              control={form.control}
              watch={form.watch}
              setValue={form.setValue}
              briefCurrency={defaultCurrency}
            />
          </Section>

          <Section title="Note">
            <NoteFields register={register} errors={formState.errors} />
          </Section>

          <CustomFieldsFieldset
            columns={customColumns}
            values={customFields}
            onChange={setCustomFields}
          />
        </div>

        <div className="flex flex-none justify-end gap-2 border-t border-u-border px-5 py-3">
          {company && onMarkNoExecutiveFound && (
            <Button
              type="button"
              variant="secondary"
              className="me-auto"
              title={`Mark ${company.companyName} as researched — nobody suitable found`}
              onClick={onMarkNoExecutiveFound}
              disabled={save.isPending}
            >
              <Icon d={ICONS.userX} size={14} />
              No executive found
            </Button>
          )}
          <Button type="button" variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={save.isPending}>
            Add executive
          </Button>
        </div>
      </form>
      </FormProvider>
    </>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mb-2 border-b border-u-border pb-2 last:border-b-0">
      <h3 className="mb-3 font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-u-text3">
        {title}
      </h3>
      {children}
    </section>
  );
}
