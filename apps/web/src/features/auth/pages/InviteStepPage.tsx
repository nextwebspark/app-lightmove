import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, FormError, Input, Logo, Notice, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";
import type { WorkspaceRole } from "../api/types";
import { INVITE_ROLES, inviteSchema } from "../schemas";
import { titleCase } from "../../../lib/format";

interface InviteRow {
  id: number;
  email: string;
  role: WorkspaceRole;
}

/**
 * Signup step 3 — "Invite your team". A port of Signup.dc.html's final step.
 *
 * Optional, and the mockup's "Skip for now" is honoured exactly: it goes to the workspace without
 * calling the API at all.
 *
 * People invited here skip the approval queue entirely. Someone who merely found this workspace on
 * their email domain has to wait for an admin to let them in; an admin naming a colleague *is* that
 * decision, made up front.
 */
export function InviteStepPage() {
  const navigate = useNavigate();
  const { user, reload } = useAuth();

  const [rows, setRows] = useState<InviteRow[]>([
    { id: 1, email: "", role: "MEMBER" },
    { id: 2, email: "", role: "MEMBER" },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Map<number, string>>(new Map());

  const update = (id: number, patch: Partial<InviteRow>) => {
    setRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
    // Clear the complaint as soon as they start fixing it, rather than making them submit to find out.
    setRowErrors((current) => {
      if (!current.has(id)) return current;
      const next = new Map(current);
      next.delete(id);
      return next;
    });
  };

  const addRow = () =>
    setRows((current) => [...current, { id: Date.now(), email: "", role: "MEMBER" }]);

  const removeRow = (id: number) =>
    setRows((current) => current.filter((row) => row.id !== id));

  const finish = async () => {
    // A typo'd address is not a harmless mistake here: the invitation is sent, the colleague never
    // receives it, and the admin has no way of knowing. The rule was already written in `inviteSchema`
    // and simply never applied — this form did no client-side validation at all.
    const parsed = inviteSchema.safeParse({ invites: rows });
    if (!parsed.success) {
      const failures = new Map<number, string>();
      for (const issue of parsed.error.issues) {
        const index = issue.path[1];
        if (typeof index === "number") {
          failures.set(rows[index].id, issue.message);
        }
      }
      setRowErrors(failures);
      return;
    }
    setRowErrors(new Map());

    // Blank rows are just an empty form, not an error. The mockup starts with two of them, and
    // refusing to continue because the user did not fill them in would be absurd.
    const filled = rows
      .filter((row) => row.email.trim() !== "")
      .map((row) => ({ email: row.email.trim(), role: row.role }));

    // Where the wizard ends. An unverified user has no workspace to land in — theirs is held until
    // they confirm their address — so "/" would only bounce them back into step 2.
    const done = user?.workspace ? "/" : "/signup/verify";

    if (filled.length === 0) {
      navigate(done, { replace: true });
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      // Held, not sent, while unverified: they go out with the workspace, when it is created. An
      // account nobody has confirmed must not be able to make LightMove email five strangers.
      await authApi.invite(filled);
      await reload();
      navigate(done, { replace: true });
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.problem.detail : "Could not send the invitations.");
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      {/* Step 2 only. Step 1 created the account — there is nothing there to go back and change. */}
      <Stepper
        steps={SIGNUP_STEPS}
        current={3}
        backableSteps={[2]}
        onGoBack={() => navigate("/signup/workspace")}
      />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        <h1 className="text-[19px] font-semibold leading-tight">Invite your team</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-text3">
          Step 3 of 3 · optional — invite people later from Team
        </p>

        <FormError message={error} />

        <div className="mb-3 flex flex-col gap-2">
          {rows.map((row) => (
            <div key={row.id} className="flex flex-wrap items-center gap-2">
              <Input
                type="email"
                value={row.email}
                onChange={(event) => update(row.id, { email: event.target.value })}
                placeholder="colleague@firm.com"
                aria-label="Colleague's email"
                invalid={rowErrors.has(row.id)}
                className="min-w-0 flex-1"
              />

              <Select
                value={row.role}
                onChange={(event) => update(row.id, { role: event.target.value as WorkspaceRole })}
                aria-label="Role"
                className="w-[130px] shrink-0"
              >
                {INVITE_ROLES.map((role) => (
                  <option key={role} value={role}>
                    {titleCase(role)}
                  </option>
                ))}
              </Select>

              <button
                type="button"
                onClick={() => removeRow(row.id)}
                disabled={rows.length === 1}
                aria-label={`Remove ${row.email || "this invite"}`}
                className="shrink-0 rounded-md p-1.5 text-text3 outline-none transition hover:text-red focus-visible:ring-2 focus-visible:ring-sky disabled:opacity-30 disabled:hover:text-text3"
              >
                ✕
              </button>

              {rowErrors.has(row.id) && (
                <span className="w-full font-mono text-[11px] text-red">{rowErrors.get(row.id)}</span>
              )}
            </div>
          ))}
        </div>

        <button
          type="button"
          onClick={addRow}
          className="mb-5 inline-flex items-center gap-1.5 text-[12.5px] font-medium text-sky hover:underline"
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Add another
        </button>

        <Notice>Invitees get access to projects you add them to — roles apply per project.</Notice>

        {/* Back beside Continue, as the mockup has it. It is safe here only because step 2 now edits the
            workspace it already created rather than trying to create a second one. */}
        <div className="flex items-center gap-2.5">
          <Button
            variant="secondary"
            className="shrink-0"
            disabled={submitting}
            onClick={() => navigate("/signup/workspace")}
          >
            Back
          </Button>

          <Button onClick={finish} loading={submitting} className="flex-1">
            Send invites &amp; finish
          </Button>
        </div>

        <button
          type="button"
          onClick={() => navigate("/", { replace: true })}
          className="mt-4 w-full text-center text-[12.5px] font-medium text-text3 hover:text-text2 hover:underline"
        >
          Skip for now
        </button>
      </Card>
    </div>
  );
}

