import { useQuery } from "@tanstack/react-query";
import { Button, HealthDot, Logo, StagePill } from "../../../components/ui";
import { formatDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as clientsApi from "../api/clientsApi";

/**
 * The client portal — a representative's read-only window onto their own client and its mandates.
 *
 * Deliberately standalone: no workspace sidebar, no staff queries. A CLIENT principal never mounts the
 * staff shell (see the router guards), and this page reads only {@code /portal/me}, which the server
 * scopes to exactly their one client.
 */
export function ClientPortalPage() {
  const { user, signOut } = useAuth();
  const { data: client, isLoading } = useQuery({
    queryKey: clientsApi.PORTAL_KEY,
    queryFn: clientsApi.portalClient,
  });

  return (
    <div className="flex min-h-screen flex-col bg-panel2">
      <header className="flex items-center justify-between border-b border-line bg-panel px-6 py-3">
        <Logo />
        <div className="flex items-center gap-3 font-mono text-[11px] text-text3">
          {user?.fullName}
          <Button variant="ghost" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[760px] px-6 py-10">
        {isLoading ? (
          <p className="font-mono text-[13px] text-text3">Loading…</p>
        ) : !client ? (
          <p className="font-mono text-[13px] text-text3">Your portal isn't ready yet.</p>
        ) : (
          <>
            <div className="font-mono text-[11px] font-medium uppercase tracking-[0.1em] text-text3">
              Client portal
            </div>
            <h1 className="mt-1 text-[22px] font-semibold">{client.name}</h1>
            <p className="mt-1 font-mono text-xs text-text3">
              {[client.sector, client.hqCountry].filter(Boolean).join(" · ") || "—"}
            </p>

            <h2 className="mb-2 mt-8 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
              Your searches
            </h2>
            {client.mandates.length === 0 ? (
              <p className="rounded-lg border border-line-soft bg-panel p-6 text-center font-mono text-[13px] text-text3">
                No searches to show yet. Your search partner will share them here.
              </p>
            ) : (
              <div className="overflow-hidden rounded-lg border border-line-soft bg-panel">
                {client.mandates.map((mandate) => (
                  <div
                    key={mandate.id}
                    className="flex items-center gap-3 border-b border-line-soft px-4 py-3 last:border-0"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[13px] font-semibold text-text">
                        {mandate.positionTitle}
                      </div>
                      <div className="font-mono text-[11px] text-text3">
                        Target · {formatDate(mandate.targetDate)}
                      </div>
                    </div>
                    <StagePill stage={mandate.stage} />
                    <HealthDot health={mandate.health} />
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
