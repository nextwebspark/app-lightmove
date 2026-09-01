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
import { SignedOutScreen } from "./screens/SignedOutScreen";

/**
 * The popup's root: which of the states the consultant is in.
 *
 * The page read and the chosen mandate are owned here so both tabs share them — otherwise a person is
 * filed into whichever mandate the tab they did not choose on defaulted to. Both panels stay mounted
 * and hidden rather than unmounted, because each owns a draft that switching tabs would destroy.
 */
export function CapturePopup() {
  const session = useExtensionSession();
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
  }, [page.subject, page.sourceUrl, settings.isPageTypeDetected]);

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

  if (!session.isPaired) {
    return <SignedOutScreen onConnected={() => void session.refresh()} />;
  }

  return (
    <PopupShell>
      <div className={cn("flex min-h-0 flex-1 flex-col overflow-hidden", isSettingsOpen && "hidden")}>
        <CaptureHeader
          user={session.user}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onSignOut={() => void session.signOut()}
        />
        <CaptureTabs active={subject} onSelect={setSubject} />
        <CapturePanel subject="company" active={subject}>
          <CaptureCompanyScreen page={page} projects={projects} />
        </CapturePanel>
        <CapturePanel subject="person" active={subject}>
          <CapturePersonScreen page={page} projects={projects} />
        </CapturePanel>
      </div>

      {isSettingsOpen && (
        <CaptureSettingsScreen
          user={session.user}
          projects={projects}
          onBack={() => setIsSettingsOpen(false)}
          onSignOut={() => void session.signOut()}
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
