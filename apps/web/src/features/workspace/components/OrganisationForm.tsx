import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as authApi from "../../auth/api/authApi";
import type { CreateWorkspaceRequest, User, WorkspaceSummary } from "../../auth/api/types";
import {
  COMPANY_SIZES,
  REGIONS,
  TEAM_FOCUSES,
  workspaceSchema,
  type WorkspaceValues,
} from "../../auth/schemas";
import { CompanyPicker, type CompanySearchSource } from "../../clients/components/CompanyPicker";
import { pickedCompanyName, workspaceCompanyPick, type CompanyPick } from "../../clients/lib/companyPick";

/**
 * The "About your organization" form — Signup.dc.html's step 3, and the first stage of the New
 * workspace modal. One form, because a workspace is described the same way whether it is the firm's
 * first or its third: the company picked from the universe, its size, region and focus. Which
 * endpoint it posts to is the caller's: the wizard creates (or corrects) through onboarding, the
 * modal through `/workspaces`.
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
    register,
    handleSubmit,
    setValue,
    getValues,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<WorkspaceValues>({
    resolver: zodResolver(workspaceSchema),
    defaultValues: {
      name: editing?.name ?? "",
      // The mockup's dropdowns open on their first option; ours were opening on the second.
      companySize: editing?.companySize ?? COMPANY_SIZES[0],
      primaryRegion: editing?.primaryRegion ?? REGIONS[0],
      teamFocus: editing?.teamFocus ?? TEAM_FOCUSES[0],
    },
  });

  // The size a database pick filled in, so dropping that pick can take it back out — unless the
  // user has since chosen a size themselves.
  const sizeFromPick = useRef<string | null>(null);

  const handlePick = (next: CompanyPick | null) => {
    setPick(next);
    setValue("name", next ? pickedCompanyName(next) : "");
    if (next) clearErrors("name");
    if (sizeFromPick.current !== null && getValues("companySize") === sizeFromPick.current) {
      setValue("companySize", COMPANY_SIZES[0]);
    }
    sizeFromPick.current = null;
    const size = next?.source === "universe" ? companySizeOf(next.company.numEmployees) : null;
    if (size) {
      setValue("companySize", size);
      sizeFromPick.current = size;
    }
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
      setFormError(
        error instanceof ApiRequestError ? error.problem.detail : "Could not save your workspace.",
      );
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">About your organization</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-u-text3">{subtitle}</p>

      <FormError message={formError} />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
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

        {/* No bottom margin: each Field already carries mb-4, and stacking the grid's own on top of the
            last row's put a double gap above Continue that the mockup does not have. */}
        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Field label="Company size">
            <Select {...register("companySize")}>
              {COMPANY_SIZES.map((size) => (
                <option key={size}>{size}</option>
              ))}
            </Select>
          </Field>

          <Field label="Primary region">
            <Select {...register("primaryRegion")}>
              {REGIONS.map((region) => (
                <option key={region}>{region}</option>
              ))}
            </Select>
          </Field>

          <Field label="Team focus">
            <Select {...register("teamFocus")}>
              {TEAM_FOCUSES.map((focus) => (
                <option key={focus}>{focus}</option>
              ))}
            </Select>
          </Field>
        </div>

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

/** The universe's headcount as one of the form's size bands; null leaves the dropdown where it was. */
function companySizeOf(numEmployees: number | null): string | null {
  if (numEmployees === null || numEmployees <= 0) return null;
  if (numEmployees <= 10) return COMPANY_SIZES[0];
  if (numEmployees <= 50) return COMPANY_SIZES[1];
  if (numEmployees <= 200) return COMPANY_SIZES[2];
  return COMPANY_SIZES[3];
}
