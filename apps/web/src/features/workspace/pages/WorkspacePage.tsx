import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Select } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { PendingMember, WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import { ThemeToggle } from "../../theme/ThemeToggle";

const PENDING_MEMBERS = ["pending-members"] as const;

/**
 * The post-login screen.
 *
 * A placeholder, deliberately. It proves the whole chain works — you signed up, verified, got into a
 * workspace, and landed somewhere only a member can reach — and it shows the empty state from
 * Workspace.dc.html. Projects themselves are not modelled yet; they arrive with the Project screen.
 *
 * The one piece of real functionality here is the pending-approval queue, because it is the other half
 * of the join flow: somebody is sitting on a "waiting for approval" screen until an admin acts, and an
 * admin who cannot see the request would leave them there.
 */
export function WorkspacePage() {
  const { user, signOut } = useAuth();
  const workspace = user?.workspace;
  const isAdmin = workspace?.role === "ADMIN";
  const verified = user?.emailVerified ?? false;

  const { data: pending } = useQuery({
    queryKey: PENDING_MEMBERS,
    queryFn: authApi.pendingMembers,
    // Every workspace-scoped route is closed to an unverified user, so asking is a guaranteed 403.
    // The answer is known here without a round-trip — don't make the request to be told it.
    enabled: isAdmin && verified,
  });

  if (!workspace) {
    return null; // The router does not route anyone here without one.
  }

  return (
    <div className="min-h-screen">
      {!verified && <UnverifiedBanner email={user!.email} />}

      <header className="flex items-center justify-between border-b border-line bg-panel px-6 py-3">
        <div className="flex items-center gap-2.5">
          <span className="grid size-7 place-items-center rounded-lg bg-amber-btn font-mono text-[13px] font-bold text-on-amber">
            {workspace.logoMark ?? workspace.name[0]}
          </span>
          <div>
            <div className="text-[13.5px] font-semibold leading-tight">{workspace.name}</div>
            <div className="font-mono text-[10.5px] text-text3">
              {workspace.emailDomain} · {titleCase(workspace.role)}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span className="font-mono text-[11px] text-text3">{user?.email}</span>
          <ThemeToggle />
          <Button variant="secondary" onClick={() => void signOut()} className="!py-1.5 !text-xs">
            Sign out
          </Button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl p-6">
        {isAdmin && pending && pending.length > 0 && (
          <section className="mb-6 animate-fade-up rounded-panel border border-line bg-panel p-5 shadow-panel">
            <h2 className="text-[15px] font-semibold">
              {pending.length} {pending.length === 1 ? "person wants" : "people want"} to join
            </h2>
            <p className="mb-4 mt-1 font-mono text-[11px] text-text3">
              They signed up on {workspace.emailDomain}. Approving grants access to this workspace.
            </p>

            <div className="flex flex-col gap-2">
              {pending.map((member) => (
                <PendingRow key={member.memberId} member={member} />
              ))}
            </div>
          </section>
        )}

        {/* The empty state from Workspace.dc.html, down to the briefcase, the 52px tile and the copy. */}
        <section className="flex animate-fade-up flex-col items-center justify-center rounded-panel border border-line bg-panel px-5 py-[70px] text-center shadow-panel [animation-delay:60ms]">
          <div className="mb-[18px] grid size-[52px] place-items-center rounded-[14px] bg-amber-dim">
            <svg className="size-6 text-amber" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2ZM16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2" />
            </svg>
          </div>

          <h2 className="mb-1.5 text-[19px] font-semibold">Create your first project</h2>
          <p className="mb-[22px] max-w-[420px] text-[13px] text-text2">
            A project holds one search mandate end to end — brief, company universe, sourcing and
            candidates.
          </p>

          <Button disabled>
            <svg className="size-[15px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
            New project
          </Button>

          {/* The pipeline a project moves through. Kept because it tells a brand-new user what this
              product actually does — which is the entire job of an empty state. */}
          <div className="mt-[34px] flex items-center gap-2.5 font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-text3">
            <span>Brief</span>
            <span className="opacity-50">→</span>
            <span>Universe</span>
            <span className="opacity-50">→</span>
            <span>Mapping</span>
            <span className="opacity-50">→</span>
            <span>Shortlist</span>
          </div>

          <p className="mt-6 font-mono text-[11px] text-text3">Coming in the next release.</p>
        </section>
      </main>
    </div>
  );
}

/**
 * One person waiting to be let in, and the admin's decision about them.
 *
 * <p>The role is chosen here, not inherited. What the applicant asked for is shown — it is useful
 * context, they know what they do — but it arrives as a suggestion and the picker starts there rather
 * than ending there. Anyone can type a role into a signup form; nobody should be able to grant
 * themselves one. This control is where the grant actually happens.
 */
function PendingRow({ member }: { member: PendingMember }) {
  const queryClient = useQueryClient();
  const [role, setRole] = useState<WorkspaceRole>(member.requestedRole);

  const settle = {
    // Refetch rather than splice the row out by hand: approving changes the member count and the
    // server owns that number.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_MEMBERS }),
  };

  const approve = useMutation({
    mutationFn: () => authApi.approveMember(member.memberId, role),
    ...settle,
  });

  const decline = useMutation({
    mutationFn: () => authApi.rejectMember(member.memberId),
    ...settle,
  });

  // A double-clicked Approve used to fire two POSTs. Deciding about a person is not an operation to do
  // twice by accident, so the whole row goes inert while either decision is in flight.
  const deciding = approve.isPending || decline.isPending;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-panel2 p-3">
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-medium">{member.fullName}</span>
        <span className="block font-mono text-[11px] text-text3">
          {member.email} · asked for {titleCase(member.requestedRole)}
        </span>
      </span>

      <Select
        value={role}
        onChange={(event) => setRole(event.target.value as WorkspaceRole)}
        disabled={deciding}
        aria-label={`Role for ${member.fullName}`}
        className="w-[130px] shrink-0"
      >
        {INVITE_ROLES.map((option) => (
          <option key={option} value={option}>
            {titleCase(option)}
          </option>
        ))}
      </Select>

      <Button
        className="!py-1.5 !text-xs"
        loading={approve.isPending}
        disabled={deciding}
        onClick={() => approve.mutate()}
      >
        Approve
      </Button>

      <Button
        variant="secondary"
        className="!py-1.5 !text-xs"
        loading={decline.isPending}
        disabled={deciding}
        onClick={() => decline.mutate()}
      >
        Decline
      </Button>

      {(approve.isError || decline.isError) && (
        <span role="alert" className="w-full font-mono text-[11px] text-red">
          That didn’t go through. Try again.
        </span>
      )}
    </div>
  );
}

/**
 * Why the workspace is inert.
 *
 * An unverified user can reach this screen but no data on it, and without saying so we leave them
 * looking at an empty page that appears simply broken. The resend link matters as much as the message:
 * the one thing they need is an email that, by definition, has not arrived.
 */
function UnverifiedBanner({ email }: { email: string }) {
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">("idle");

  const resend = async () => {
    setState("sending");
    try {
      // The endpoint answers 202 even for an address it has never seen, so a *rejection* here is never
      // "no such user" — it is a rate limit (three an hour) or the network. Both are worth saying, and
      // both must give the button back. Without this catch a 429 left it reading "Sending…" forever,
      // which is the one state from which the user can do nothing at all.
      await authApi.resendVerification(email);
      setState("sent");
    } catch {
      setState("error");
    }
  };

  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1.5 bg-amber-dim px-6 py-2.5 text-center font-mono text-[11.5px] text-amber"
    >
      <span>Confirm {email} to unlock your workspace.</span>

      {state === "sent" ? (
        <span className="text-text3">Sent — check your inbox.</span>
      ) : (
        <>
          {state === "error" && <span className="text-red">Couldn’t send.</span>}
          <button
            type="button"
            onClick={() => void resend()}
            disabled={state === "sending"}
            className="font-semibold underline underline-offset-2 hover:no-underline disabled:opacity-50"
          >
            {state === "sending" ? "Sending…" : state === "error" ? "Try again" : "Resend email"}
          </button>
        </>
      )}
    </div>
  );
}

function titleCase(role: string): string {
  return role.charAt(0) + role.slice(1).toLowerCase();
}
