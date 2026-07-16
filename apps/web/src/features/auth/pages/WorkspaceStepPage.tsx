import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";
import type { WorkspaceSummary } from "../api/types";
import {
  COMPANY_SIZES,
  JOB_TITLES,
  REGIONS,
  TEAM_FOCUSES,
  workspaceSchema,
  type WorkspaceValues,
} from "../schemas";

/**
 * Signup step 2 — creating your workspace.
 *
 * Signing up *is* creating a workspace; membership of an existing one is invitation-only, so there is
 * no domain lookup and no join fork here. A colleague whose firm is already on LightMove asks their
 * admin for an invitation — the admin reaching out is the decision that admits them.
 */
export function WorkspaceStepPage() {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  // Already made one, and came back — via the Back button on step 3, or by reopening the tab. This step
  // *commits*, unlike the mockup's wizard, so returning to it cannot mean "create": it means "correct
  // what you created". Without this the only thing the form could produce is a 409.
  const existing = user?.workspace ?? null;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />
      <Stepper steps={SIGNUP_STEPS} current={2} />

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

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<WorkspaceValues>({
    resolver: zodResolver(workspaceSchema),
    defaultValues: {
      name: editing?.name ?? "",
      // The mockup's dropdowns open on their first option; ours were opening on the second.
      companySize: COMPANY_SIZES[0],
      primaryRegion: REGIONS[0],
      jobTitle: JOB_TITLES[0],
      teamFocus: TEAM_FOCUSES[0],
    },
  });

  const onSubmit = async (values: WorkspaceValues) => {
    setFormError(null);
    try {
      await (editing ? authApi.updateWorkspace(values) : authApi.createWorkspace(values));
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
        Step 2 of 3 · {editing ? "update your workspace" : "this becomes your workspace"}
      </p>

      <FormError message={formError} />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Field
          label="Organization name"
          error={errors.name?.message}
          hint="This becomes your workspace name — you can change it later."
        >
          <Input
            autoFocus
            placeholder="e.g. LightMove Search Partners"
            invalid={!!errors.name}
            {...register("name")}
          />
        </Field>

        {/* No bottom margin: each Field already carries mb-4, and stacking the grid's own on top of the
            last row's put a double gap above Continue that the mockup does not have. */}
        <div className="grid grid-cols-2 gap-x-4">
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

          {/* "Your role" in the mockup. It is a job title and lands on the person — the workspace role
              is separate, and whoever creates the workspace is always its ADMIN. */}
          <Field label="Your role">
            <Select {...register("jobTitle")}>
              {JOB_TITLES.map((title) => (
                <option key={title}>{title}</option>
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
