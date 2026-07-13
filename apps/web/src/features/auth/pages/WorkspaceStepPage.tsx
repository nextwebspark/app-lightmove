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
import type { JoinableWorkspace, Workspace } from "../api/types";
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
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  // Already made one, and came back — via the Back button on step 3, or by reopening the tab. This step
  // *commits*, unlike the mockup's wizard, so returning to it cannot mean "create": it means "correct
  // what you created". Without this the only thing the form could produce is a 409.
  const existing = user?.workspace ?? null;

  const { data: joinable, isLoading } = useQuery({
    queryKey: ["joinable-workspaces"],
    queryFn: authApi.joinableWorkspaces,
    // A user who already belongs somewhere is not choosing between workspaces.
    enabled: !existing,
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
        {existing ? (
          <CreateWorkspace
            editing={existing}
            canJoinInstead={false}
            onJoinInstead={() => {}}
            onCreated={async () => {
              await reload();
              navigate("/signup/invite", { replace: true });
            }}
          />
        ) : effectiveMode === "join" && joinable && joinable.length > 0 ? (
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
            editing={null}
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
            // The radio itself is sr-only (the whole card is the control), so without a focus ring on
            // the label a keyboard user moving through the list can see nothing move.
            className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-sky ${
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

      {/* A real button, not a text link. This is a fork, not an escape hatch: a firm may legitimately
          run several workspaces, and someone who wants their own must be able to see that they can
          have one — without it, joining looks like the only way forward. */}
      <Button variant="secondary" onClick={onCreateInstead} className="mt-2.5 w-full">
        Create a separate workspace instead
      </Button>
    </>
  );
}

// ── Path B: create your own ─────────────────────────────────────────────────

function CreateWorkspace({
  editing,
  canJoinInstead,
  onJoinInstead,
  onCreated,
}: {
  /** The workspace they already made, if they came back to this step. See WorkspaceStepPage. */
  editing: Workspace | null;
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
        <Button variant="secondary" onClick={onJoinInstead} className="mt-2.5 w-full">
          Join an existing workspace instead
        </Button>
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
