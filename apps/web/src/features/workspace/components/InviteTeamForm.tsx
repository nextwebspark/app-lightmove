import { useState, type ReactNode } from "react";
import { Button, FormError, Input, Notice, Select } from "../../../components/ui";
import { titleCase } from "../../../lib/format";
import type { InviteRequest, WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES, inviteSchema } from "../../auth/schemas";
import { messageFor } from "../../../lib/errorCodes";

interface InviteRow {
  id: number;
  email: string;
  role: WorkspaceRole;
}

/**
 * "Invite your team" — Signup.dc.html's final step, and the second stage of the New workspace modal.
 * Rows of address + role, validated before anything is sent, and skippable: blank rows are an empty
 * form, not an error, and "Skip for now" sends nothing at all.
 *
 * People invited here skip any approval: an admin naming a colleague *is* the decision, made up front.
 */
export function InviteTeamForm({
  subtitle,
  submit,
  onDone,
  onSkip,
  before,
  finishLabel = "Send invites & finish",
}: {
  subtitle: string;
  submit: (invites: InviteRequest[]) => Promise<unknown>;
  /** After the invitations went out, or when there were none to send. */
  onDone: () => Promise<void> | void;
  onSkip: () => void;
  /** Rendered beside the finish button — the wizard's Back. */
  before?: (submitting: boolean) => ReactNode;
  finishLabel?: string;
}) {
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
    // receives it, and the admin has no way of knowing. So the rule in `inviteSchema` runs first.
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

    if (filled.length === 0) {
      await onDone();
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await submit(filled);
      await onDone();
    } catch (err) {
      setError(messageFor(err));
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1 className="text-[19px] font-semibold leading-tight">Invite your team</h1>
      <p className="mb-6 mt-1 font-mono text-xs text-u-text3">{subtitle}</p>

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
              className="shrink-0 rounded-md p-1.5 text-u-text3 outline-none transition hover:text-u-offlimits focus-visible:ring-2 focus-visible:ring-u-accent disabled:opacity-30 disabled:hover:text-u-text3"
            >
              ✕
            </button>

            {rowErrors.has(row.id) && (
              <span className="w-full font-mono text-meta text-u-offlimits">{rowErrors.get(row.id)}</span>
            )}
          </div>
        ))}
      </div>

      <button
        type="button"
        onClick={addRow}
        className="mb-5 inline-flex items-center gap-1.5 text-[12.5px] font-medium text-u-accent hover:underline"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
        Add another
      </button>

      <Notice>Invitees get access to projects you add them to — roles apply per project.</Notice>

      <div className="flex items-center gap-2.5">
        {before?.(submitting)}
        <Button onClick={finish} loading={submitting} className="flex-1">
          {finishLabel}
        </Button>
      </div>

      <button
        type="button"
        onClick={onSkip}
        className="mt-4 w-full text-center text-[12.5px] font-medium text-u-text3 hover:text-u-text2 hover:underline"
      >
        Skip for now
      </button>
    </>
  );
}
