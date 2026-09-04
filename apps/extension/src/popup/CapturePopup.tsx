import { useEffect, useState } from "react";
import { capturePanelId, type CaptureSubject } from "../domain/captureSubject";
import { CaptureHeader } from "./components/CaptureHeader";
import { CaptureTabs } from "./components/CaptureTabs";
import { PopupShell } from "./components/PopupChrome";
import { useActivePage } from "./hooks/useActivePage";
import { useCaptureSettings } from "./hooks/useCaptureSettings";
import { useExtensionSession } from "./hooks/useExtensionSession";
import { useProjectSelection } from "./hooks/useProjectSelection";
import { cn } from "./lib/cn";
import { CaptureCompanyScreen } from "./screens/CaptureCompanyScreen";
import { CapturePersonScreen } from "./screens/CapturePersonScreen";
import { CaptureSettingsScreen } from "./screens/CaptureSettingsScreen";
import { PanelLoading } from "./components/PanelLoading";
import { SignedOutScreen } from "./screens/SignedOutScreen";
import type { WorkspaceUser } from "../api/types";

/**
 * The popup's root: which of the states the consultant is in.
 *
 * The page read and the chosen mandate are owned here so both tabs share them — otherwise a person is
 * filed into whichever mandate the tab they did not choose on defaulted to. Both panels stay mounted
 * and hidden rather than unmounted, because each owns a draft that switching tabs would destroy.
 */
export function CapturePopup() {
  const session = useExtensionSession();

  if (session.isLoading) {
    return (
      <PopupShell>
        <div className="flex flex-1 items-center justify-center text-[12.5px] text-text3">Loading…</div>
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

/** The paired panel: what is on the page, which mandate it lands in, and the two forms over both. */
function CaptureSession({ user, onSignOut }: CaptureSessionProps) {
  const page = useActivePage();
  const projects = useProjectSelection();
  const { settings } = useCaptureSettings();
  const [subject, setSubject] = useState<CaptureSubject>("company");
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  // The tab follows the page: a profile selects Person, a company page selects Company, on every
  // navigation — keyed on the page's URL, so moving to a new page of the *same* kind realigns too.
  // It cannot yank the tab from under an editor: re-reads of the unchanged page (a title blink)
  // keep both dependency values identical, and "unknown" never guesses.
  useEffect(() => {
    if (!settings.isPageTypeDetected || !page.subject || page.subject === "unknown") {
      return;
    }
    setSubject(page.subject);
  }, [page.subject, page.pageKey, settings.isPageTypeDetected]);

  return (
    <PopupShell>
      <div className={cn("flex min-h-0 flex-1 flex-col overflow-hidden", isSettingsOpen && "hidden")}>
        <CaptureHeader
          user={user}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onSignOut={onSignOut}
        />
        <CaptureTabs active={subject} onSelect={setSubject} />
        {/* The one read with no form worth leaving on screen. Every later read shimmers in place. */}
        {page.hasReadOnce ? (
          <>
            <CapturePanel subject="company" active={subject}>
              <CaptureCompanyScreen page={page} projects={projects} />
            </CapturePanel>
            <CapturePanel subject="person" active={subject}>
              <CapturePersonScreen page={page} projects={projects} />
            </CapturePanel>
          </>
        ) : (
          <PanelLoading label="Reading this page…" />
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
    </PopupShell>
  );
}

/** One tab's form, hidden when it is not the active tab — never unmounted, so its draft survives. */
function CapturePanel({
  subject,
  active,
  children,
}: {
  subject: CaptureSubject;
  active: CaptureSubject;
  children: React.ReactNode;
}) {
  return (
    <div
      role="tabpanel"
      id={capturePanelId(subject)}
      aria-labelledby={`capture-tab-${subject}`}
      hidden={subject !== active}
      className={cn("flex min-h-0 flex-1 flex-col overflow-hidden", subject !== active && "hidden")}
    >
      {children}
    </div>
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
      <h1 className="text-[15px] font-semibold">LightMove Capture is not responding</h1>
      <p className="mt-2 max-w-[280px] text-[12.5px] leading-[1.6] text-text2">
        {message ?? "Its background worker did not answer."} Your session is untouched — try again
        rather than reconnecting.
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 rounded-lg bg-amber-btn px-4 py-[9px] text-[13px] font-semibold text-on-amber"
      >
        Try again
      </button>
    </div>
  );
}
