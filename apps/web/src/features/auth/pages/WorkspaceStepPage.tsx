import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { AuthLogo, Button, Card, Field, FormError, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { CompanyPicker, type CompanySearchSource } from "../../clients/components/CompanyPicker";
import { pickedCompanyName, type CompanyPick } from "../../clients/lib/companyPick";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";
import type { WorkspaceSummary } from "../api/types";
import {
  COMPANY_SIZES,
  REGIONS,
  TEAM_FOCUSES,
  workspaceSchema,
  type WorkspaceValues,
} from "../schemas";

/**
 * Signup step 3 — creating your workspace.
 *
 * Signing up *is* creating a workspace; membership of an existing one is invitation-only, so there is
 * no domain lookup and no join fork here. A colleague whose firm is already on LightMove asks their
 * admin for an invitation — the admin reaching out is the decision that admits them.
 */
export function WorkspaceStepPage() {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  // Already made one, and came back — via the Back button on the invite step, or by reopening the tab. This step
  // *commits*, unlike the mockup's wizard, so returning to it cannot mean "create": it means "correct
  // what you created". Without this the only thing the form could produce is a 409.
  const existing = user?.workspace ?? null;

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />
      <Stepper steps={SIGNUP_STEPS} current={3} />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        <CreateWorkspace
          editing={existing}
          onCreated={async () => {
            await reload();
            navigate("/signup/invite", { replace: true });
          }}
        />
      </Card>
    </div>
  );
}

function CreateWorkspace({
  editing,
  onCreated,
}: {
  /** The workspace they already made, if they came back to this step. See WorkspaceStepPage. */
  editing: WorkspaceSummary | null;
  onCreated: () => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const [pick, setPick] = useState<CompanyPick | null>(() => (editing ? pickOf(editing) : null));

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
      await (editing ? authApi.updateWorkspace(payload) : authApi.createWorkspace(payload));
      await onCreated();
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError ? error.problem.detail : "Could not save your workspace.",
      );
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">About your organization</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-text3">
        Step 3 of 4 · {editing ? "update your workspace" : "this becomes your workspace"}
      </p>

      <FormError message={formError} />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {pick && (
          <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
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
          Continue
        </Button>
      </form>
    </>
  );
}

const ONBOARDING_COMPANY_SEARCH: CompanySearchSource = {
  key: authApi.ONBOARDING_COMPANY_SEARCH_KEY,
  search: authApi.searchOnboardingCompanies,
};

/** The workspace they already made, as the picker shows a pick: the universe row it was filed under, or its typed name. */
function pickOf(workspace: WorkspaceSummary): CompanyPick {
  const { company } = workspace;
  if (!company) return { source: "custom", name: workspace.name, domain: "", hqCountry: "" };
  return {
    source: "universe",
    company: {
      apolloAccountId: company.apolloAccountId,
      companyName: workspace.name,
      industry: company.industry,
      companyCity: company.city,
      companyCountry: company.country,
      website: company.website,
      logoUrl: company.logoUrl,
      numEmployees: null,
    },
  };
}

/** The universe's headcount as one of the step's size bands; null leaves the dropdown where it was. */
function companySizeOf(numEmployees: number | null): string | null {
  if (numEmployees === null || numEmployees <= 0) return null;
  if (numEmployees <= 10) return COMPANY_SIZES[0];
  if (numEmployees <= 50) return COMPANY_SIZES[1];
  if (numEmployees <= 200) return COMPANY_SIZES[2];
  return COMPANY_SIZES[3];
}
