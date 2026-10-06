import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Button, FormError } from "../../../components/ui";
import * as authApi from "../../auth/api/authApi";
import type { CreateWorkspaceRequest, User, WorkspaceSummary } from "../../auth/api/types";
import { workspaceSchema, type WorkspaceValues } from "../../auth/schemas";
import { CompanyPicker, type CompanySearchSource } from "../../clients/components/CompanyPicker";
import { pickedCompanyName, workspaceCompanyPick, type CompanyPick } from "../../clients/lib/companyPick";
import { messageFor } from "../../../lib/errorCodes";
import { WorkspaceModeChoice } from "./WorkspaceModeChoice";

/**
 * The "About your organization" form — Signup.dc.html's step 3, and the first stage of the New
 * workspace modal. One form, because a workspace is described the same way whether it is the firm's
 * first or its third: who it hires for and the company picked from the universe. Which endpoint it posts to is the caller's: the wizard creates (or corrects) through
 * onboarding, the modal through `/workspaces`.
 */
export function OrganisationForm({
  editing,
  subtitle,
  submitLabel = "Continue",
  submit,
  onDone,
}: {
  /** The workspace being corrected, if the caller came back to a committed step; null to create. */
  editing: WorkspaceSummary | null;
  subtitle: string;
  submitLabel?: string;
  submit: (payload: CreateWorkspaceRequest) => Promise<User>;
  /** Given the server's answer; the caller decides where the user goes next. May throw to keep the form open. */
  onDone: (user: User) => Promise<void> | void;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const [pick, setPick] = useState<CompanyPick | null>(() =>
    editing ? workspaceCompanyPick(editing.name, editing.company) : null,
  );

  const {
    control,
    register,
    handleSubmit,
    setValue,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<WorkspaceValues>({
    resolver: zodResolver(workspaceSchema),
    defaultValues: {
      mode: editing?.mode,
      name: editing?.name ?? "",
    },
  });

  const handlePick = (next: CompanyPick | null) => {
    setPick(next);
    setValue("name", next ? pickedCompanyName(next) : "");
    if (next) clearErrors("name");
  };

  const onSubmit = async (values: WorkspaceValues) => {
    setFormError(null);
    const payload = {
      ...values,
      apolloAccountId: pick?.source === "universe" ? pick.company.apolloAccountId : null,
    };
    try {
      await onDone(await submit(payload));
    } catch (error) {
      setFormError(messageFor(error));
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">About your organization</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-u-text3">{subtitle}</p>

      <FormError message={formError} />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Controller
          control={control}
          name="mode"
          render={({ field, fieldState }) => (
            <WorkspaceModeChoice
              value={field.value ?? null}
              onChange={field.onChange}
              error={fieldState.error?.message}
            />
          )}
        />

        {pick && (
          <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
            Organization name
          </span>
        )}
        {/* The picker's result list carries no margin of its own; the picked card and the bare field do. */}
        <div className={pick ? undefined : "mb-4"}>
          <CompanyPicker
            label="Organization name"
            pick={pick}
            onPick={handlePick}
            source={ONBOARDING_COMPANY_SEARCH}
            asksCustomDetails={false}
            // Typing is not choosing: the name is only set by a pick, so say how to make one.
            error={errors.name ? "Choose your organization from the list, or add it as new" : undefined}
            autoFocus
          />
        </div>
        <input type="hidden" {...register("name")} />

        <Button type="submit" loading={isSubmitting} className="w-full">
          {submitLabel}
        </Button>
      </form>
    </>
  );
}

/**
 * The organisation form's own read of the universe: `/companies/search` needs a workspace, which the
 * person on the wizard's step is in the middle of creating — and the modal reads the same one so the
 * two never pick from different lists.
 */
const ONBOARDING_COMPANY_SEARCH: CompanySearchSource = {
  key: authApi.ONBOARDING_COMPANY_SEARCH_KEY,
  search: authApi.searchOnboardingCompanies,
};

