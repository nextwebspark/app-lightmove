import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo, Notice, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";
import type { JoinableWorkspace } from "../api/types";
import {
  COMPANY_SIZES,
  JOB_TITLES,
  REGIONS,
  TEAM_FOCUSES,
  workspaceSchema,
  type WorkspaceValues,
} from "../schemas";

/**
 * Signup step 2 — where a user ends up in a workspace.
 *
 * The mockup only knew how to create one. The real flow has a fork, because a firm may already be on
 * LightMove: if any workspace exists on the user's email domain, they are shown it and can ask to
 * join instead of unwittingly starting a second copy of their own company.
 *
 * Asking to join grants nothing. An admin has to approve it, and until they do the user has no access
 * to anything — sharing an employer's email domain is evidence someone works there, not a decision
 * that they should see an executive-search pipeline.
 */
export function WorkspaceStepPage() {
  const { reload } = useAuth();
  const navigate = useNavigate();

  const { data: joinable, isLoading } = useQuery({
    queryKey: ["joinable-workspaces"],
    queryFn: authApi.joinableWorkspaces,
  });

  // Default to joining when their firm is already here — that is almost always what they want, and
  // the alternative (a duplicate workspace nobody can see into) is the expensive mistake.
  const [mode, setMode] = useState<"join" | "create" | null>(null);
  const effectiveMode = mode ?? (joinable && joinable.length > 0 ? "join" : "create");

  if (isLoading) {
    return <Centered>Checking your organization…</Centered>;
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />
      <Stepper steps={SIGNUP_STEPS} current={2} />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        {effectiveMode === "join" && joinable && joinable.length > 0 ? (
          <JoinExisting
            workspaces={joinable}
            onCreateInstead={() => setMode("create")}
            onJoined={async () => {
              await reload();
              navigate("/signup/pending", { replace: true });
            }}
          />
        ) : (
          <CreateWorkspace
            canJoinInstead={!!joinable && joinable.length > 0}
            onJoinInstead={() => setMode("join")}
            onCreated={async () => {
              await reload();
              navigate("/signup/invite", { replace: true });
            }}
          />
        )}
      </Card>
    </div>
  );
}

// ── Path A: join a workspace that already exists on your domain ──────────────

function JoinExisting({
  workspaces,
  onCreateInstead,
  onJoined,
}: {
  workspaces: JoinableWorkspace[];
  onCreateInstead: () => void;
  onJoined: () => Promise<void>;
}) {
  const [selected, setSelected] = useState(workspaces[0].id);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      // RESEARCHER is a request, not a claim. The approving admin picks the real role — which is what
      // stops anyone walking in and declaring themselves an administrator.
      await authApi.requestToJoin(selected, "RESEARCHER");
      await onJoined();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.problem.detail : "Could not send your request.");
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">Join your organization</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-text3">
        Step 2 of 3 · we found your firm on LightMove
      </p>

      <FormError message={error} />

      <div className="mb-4 flex flex-col gap-2">
        {workspaces.map((workspace) => (
          <label
            key={workspace.id}
            className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition ${
              selected === workspace.id ? "border-amber-btn bg-amber-dim" : "border-line bg-panel2"
            }`}
          >
            <input
              type="radio"
              name="workspace"
              value={workspace.id}
              checked={selected === workspace.id}
              onChange={() => setSelected(workspace.id)}
              className="sr-only"
            />

            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-sky-dim font-mono text-sm font-bold text-sky">
              {workspace.logoMark ?? workspace.name[0]}
            </span>

            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13.5px] font-semibold">{workspace.name}</span>
              <span className="block font-mono text-[11px] text-text3">
                {workspace.memberCount} {workspace.memberCount === 1 ? "member" : "members"}
                {workspace.adminName && ` · run by ${workspace.adminName}`}
              </span>
            </span>

            {selected === workspace.id && (
              <svg className="size-4 text-amber" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            )}
          </label>
        ))}
      </div>

      <Notice>An administrator will review your request before you get access.</Notice>

      <Button onClick={submit} loading={submitting} className="w-full">
        Ask to join
      </Button>

      <button
        type="button"
        onClick={onCreateInstead}
        className="mt-4 w-full text-center text-[12.5px] font-medium text-text3 hover:text-text2 hover:underline"
      >
        Create a separate workspace instead
      </button>
    </>
  );
}

// ── Path B: create your own ─────────────────────────────────────────────────

function CreateWorkspace({
  canJoinInstead,
  onJoinInstead,
  onCreated,
}: {
  canJoinInstead: boolean;
  onJoinInstead: () => void;
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
      name: "",
      companySize: COMPANY_SIZES[1],
      primaryRegion: REGIONS[0],
      jobTitle: JOB_TITLES[0],
      teamFocus: TEAM_FOCUSES[0],
    },
  });

  const onSubmit = async (values: WorkspaceValues) => {
    setFormError(null);
    try {
      await authApi.createWorkspace(values);
      await onCreated();
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError ? error.problem.detail : "Could not create your workspace.",
      );
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">About your organization</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-text3">
        Step 2 of 3 · this becomes your workspace
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

        <div className="mb-5 grid grid-cols-2 gap-x-4">
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

      {canJoinInstead && (
        <button
          type="button"
          onClick={onJoinInstead}
          className="mt-4 w-full text-center text-[12.5px] font-medium text-text3 hover:text-text2 hover:underline"
        >
          Join an existing workspace instead
        </button>
      )}
    </>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
      <Logo />
      <p className="font-mono text-xs text-text3">{children}</p>
    </div>
  );
}
