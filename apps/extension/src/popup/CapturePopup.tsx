import { useState } from "react";
import { CaptureHeader } from "./components/CaptureHeader";
import { PageReadNote } from "./components/PageReadNote";
import { PanelToastProvider } from "./components/PanelToast";
import { PopupShell } from "./components/PopupChrome";
import { PositionBar } from "./components/PositionBar";
import { useActivePage } from "./hooks/useActivePage";
import { useExtensionSession } from "./hooks/useExtensionSession";
import { useProjectSelection } from "./hooks/useProjectSelection";
import { cn } from "./lib/cn";
import { CaptureSettingsScreen } from "./screens/CaptureSettingsScreen";
import { CompanyPanel } from "./screens/CompanyPanel";
import { PersonPanel } from "./screens/PersonPanel";
import { PanelLoading } from "./components/PanelLoading";
import { SignedOutScreen } from "./screens/SignedOutScreen";
import type { WorkspaceUser } from "../api/types";

/**
 * The popup's root: which of the states the consultant is in.
 *
 * The page read and the chosen position are owned here, once, so a person and a company are filed into the same
 * position the bar shows.
 */
export function CapturePopup() {
  const session = useExtensionSession();

  if (session.isLoading) {
    return (
      <PopupShell>
        <div className="flex flex-1 items-center justify-center text-[12.5px] text-u-text3">Loading…</div>
      </PopupShell>
    );
  }

  if (session.hasFailed) {
    return (
      <PopupShell>
        <SessionUnreachable message={session.failure} onRetry={() => void session.refresh()} />
      </PopupShell>
    );
  }

  if (!session.user) {
    return <SignedOutScreen onConnected={() => void session.refresh()} />;
  }

  // Keyed on who is paired, so re-pairing as somebody else remounts rather than inheriting: the
  // chosen mandate is component state down there, and a shared laptop would otherwise file the next
  // consultant's first capture into the previous one's mandate.
  return (
    <CaptureSession
      key={session.user.id}
      user={session.user}
      onSignOut={() => void session.signOut()}
    />
  );
}

interface CaptureSessionProps {
  user: WorkspaceUser;
  onSignOut: () => void;
}

/**
 * The paired panel: the position it files into, chosen at the top and kept, then whatever the page is about — a
 * person or a company, decided by the page's address alone.
 */
function CaptureSession({ user, onSignOut }: CaptureSessionProps) {
  const page = useActivePage();
  const projects = useProjectSelection();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  return (
    <PopupShell>
      <PanelToastProvider>
        <div className={cn("flex min-h-0 flex-1 flex-col overflow-hidden", isSettingsOpen && "hidden")}>
          <CaptureHeader user={user} onOpenSettings={() => setIsSettingsOpen(true)} onSignOut={onSignOut} />
          <PositionBar projects={projects} workspaceName={user.workspace?.name ?? null} />
          {/* The one read with no form worth leaving on screen. Every later read shimmers in place. */}
          {!page.hasReadOnce ? (
            <PanelLoading label="Reading this page…" />
          ) : page.readError || page.subject === "unknown" || !page.subject ? (
            <div className="min-h-0 flex-1 overflow-y-auto p-3.5">
              <PageReadNote error={page.readError} selectedProjectId={projects.selectedProjectId} />
            </div>
          ) : page.subject === "person" ? (
            <PersonPanel key={page.pageKey} page={page} projects={projects} user={user} />
          ) : (
            <CompanyPanel key={page.pageKey} page={page} projects={projects} user={user} />
          )}
        </div>

        {isSettingsOpen && (
          <CaptureSettingsScreen
            user={user}
            projects={projects}
            onBack={() => setIsSettingsOpen(false)}
            onSignOut={onSignOut}
          />
        )}
      </PanelToastProvider>
    </PopupShell>
  );
}

/**
 * The worker did not answer.
 *
 * Deliberately not the signed-out screen: that one invites a re-pair, and pairing revokes the session
 * the extension is holding — so a transient blip would cost a consultant a perfectly good credential.
 */
function SessionUnreachable({ message, onRetry }: { message: string | null; onRetry: () => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
      <h1 className="text-[15px] font-semibold">UNCAVA Capture is not responding</h1>
      <p className="mt-2 max-w-[280px] text-[12.5px] leading-[1.6] text-u-text2">
        {message ?? "Its background worker did not answer."} Your session is untouched — try again
        rather than reconnecting.
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 rounded-lg bg-u-accent-solid px-4 py-[9px] text-[13px] font-semibold text-white"
      >
        Try again
      </button>
    </div>
  );
}
