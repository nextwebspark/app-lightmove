import { useQuery } from "@tanstack/react-query";
import { Button } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";

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

  const { data: pending, refetch } = useQuery({
    queryKey: ["pending-members"],
    queryFn: authApi.pendingMembers,
    enabled: isAdmin,
  });

  if (!workspace) {
    return null; // The router does not route anyone here without one.
  }

  return (
    <div className="min-h-screen">
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
                <div
                  key={member.memberId}
                  className="flex items-center gap-3 rounded-lg border border-line bg-panel2 p-3"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[13px] font-medium">{member.fullName}</span>
                    <span className="block font-mono text-[11px] text-text3">{member.email}</span>
                  </span>

                  <Button
                    className="!py-1.5 !text-xs"
                    onClick={async () => {
                      // RESEARCHER, not the role they asked for. The admin decides, which is the whole
                      // point of the approval step.
                      await authApi.approveMember(member.memberId, "RESEARCHER");
                      await refetch();
                    }}
                  >
                    Approve
                  </Button>

                  <Button
                    variant="secondary"
                    className="!py-1.5 !text-xs"
                    onClick={async () => {
                      await authApi.rejectMember(member.memberId);
                      await refetch();
                    }}
                  >
                    Decline
                  </Button>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* The empty state from Workspace.dc.html. */}
        <section className="animate-fade-up rounded-panel border border-line bg-panel p-12 text-center shadow-panel [animation-delay:60ms]">
          <div className="mx-auto mb-4 grid size-12 place-items-center rounded-xl bg-amber-dim">
            <svg className="size-6 text-amber" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
              <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            </svg>
          </div>

          <h2 className="text-[17px] font-semibold">Create your first project</h2>
          <p className="mx-auto mb-6 mt-2 max-w-sm text-[13px] leading-relaxed text-text2">
            A project is a search mandate — one client, one role to fill. Track the universe, the
            mapping and the shortlist in one place.
          </p>

          <Button disabled className="mx-auto">
            New project
          </Button>
          <p className="mt-3 font-mono text-[11px] text-text3">Coming in the next release.</p>
        </section>
      </main>
    </div>
  );
}

function titleCase(role: string): string {
  return role.charAt(0) + role.slice(1).toLowerCase();
}
