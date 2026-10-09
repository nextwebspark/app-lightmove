import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Button, FormError } from "../../../components/ui";
import * as authApi from "../../auth/api/authApi";
import type { CreateWorkspaceRequest, User, WorkspaceSummary } from "../../auth/api/types";
import { workspaceSchema, type WorkspaceValues } from "../../auth/schemas";
import type { CompanySearchSource } from "../../clients/components/CompanyPicker";
import { workspaceCompanyPick } from "../../clients/lib/companyPick";
import type { CompanySuggestion } from "../../strategy/api/types";
import { messageFor } from "../../../lib/errorCodes";
import { FirmNameField } from "./FirmNameField";
import { WorkspaceModeChoice } from "./WorkspaceModeChoice";

/**
 * The "About your organization" form — Signup.dc.html's step 3, and the first stage of the New
 * workspace modal. One form, because a workspace is described the same way whether it is the firm's
 * first or its third: who it hires for and its name, optionally matched to the universe. Which endpoint it
 * posts to is the caller's: the wizard creates (or corrects) through onboarding, the modal through
 * `/workspaces`.
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
  const [match, setMatch] = useState<CompanySuggestion | null>(() => {
    if (!editing?.company) return null;
    const pick = workspaceCompanyPick(editing.name, editing.company);
    return pick.source === "universe" ? pick.company : null;
  });

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<WorkspaceValues>({
    resolver: zodResolver(workspaceSchema),
    defaultValues: {
      mode: editing?.mode,
      name: editing?.name ?? "",
    },
  });
  const mode = watch("mode");
  const name = watch("name");

  const onSubmit = async (values: WorkspaceValues) => {
    setFormError(null);
    const payload = { ...values, name: values.name.trim(), apolloAccountId: match?.apolloAccountId ?? null };
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

        <FirmNameField
          label={mode === "AGENCY" ? "Your firm's name" : mode === "COMPANY" ? "Your company's name" : "Your firm or company's name"}
          name={name}
          onNameChange={(next) => setValue("name", next, { shouldValidate: !!errors.name })}
          match={match}
          onMatch={setMatch}
          source={ONBOARDING_COMPANY_SEARCH}
          error={errors.name?.message}
        />

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
