import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, FormError, useToast } from "../../../components/ui";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import type { CustomColumn, CustomFieldValues } from "../../customcolumns/api/types";
import { CustomFieldsFieldset } from "../../customcolumns/components/CustomFieldsFieldset";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import {
  candidateSchema,
  employedCandidateSchema,
  EMPTY_FORM,
  payloadOf,
  type CandidateForm,
  type ParsedCandidateForm,
} from "../lib/candidateForm";
import {
  BackgroundFields,
  CareerFields,
  CompensationFields,
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
 * employer in that case anyway. Opened from the toolbar the field is free text and <b>required</b> —
 * the server files whatever is typed into the mandate's universe and maps the person to it, so the
 * grid gets a company line rather than a person beside one.
 *
 * <p>Mounted fresh each time the panel opens, so a reopen never shows the half-typed profile that
 * was abandoned last time.
 */
export function AddCandidateForm({
  projectId,
  company,
  customColumns,
  onClose,
  onSaved,
}: {
  projectId: string;
  company: CandidateCompanyContext | null;
  /** This mandate's own person columns, filled in the same save as the fields above them. */
  customColumns: readonly CustomColumn[];
  onClose: () => void;
  /** The created profile — the panel moves on to reading it. */
  onSaved: (saved: Candidate) => void;
}) {
  const toast = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Outside react-hook-form: its schema is a fixed shape and these keys are the project's, decided at
  // runtime. They still travel in the same submit, so a save is one request and one audit event.
  const [customFields, setCustomFields] = useState<CustomFieldValues>({});

  const form = useForm<CandidateForm, unknown, ParsedCandidateForm>({
    resolver: zodResolver(company ? candidateSchema : employedCandidateSchema),
    defaultValues: { ...EMPTY_FORM, employerName: company?.companyName ?? "" },
  });
  const { register, formState } = form;

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
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <DrawerCloseButton onClose={onClose} />
        <h2 className="font-sans text-base font-semibold">Add executive</h2>
        <p className="mt-1 pe-8 font-mono text-[11.5px] text-text3">
          {company
            ? `At ${company.companyName}`
            : "Name their employer below — it joins this mandate's universe if it isn't there yet."}
        </p>
      </div>

      <form
        onSubmit={form.handleSubmit((parsed) => {
          setSubmitError(null);
          save.mutate(parsed);
        })}
        noValidate
        className="flex min-h-0 flex-1 flex-col"
      >
        <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
          <FormError message={submitError} />

          <Section title="Identity">
            <IdentityFields
              register={register}
              errors={formState.errors}
              employerLocked={company !== null}
              autoFocus
              statusField={<StatusField register={register} errors={formState.errors} />}
            />
          </Section>

          <Section title="Contact">
            <ContactFields register={register} errors={formState.errors} />
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
              watch={form.watch}
              setValue={form.setValue}
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

        <div className="flex flex-none justify-end gap-2 border-t border-line-soft px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={save.isPending}>
            Add executive
          </Button>
        </div>
      </form>
    </>
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
