import { useEffect, useRef, useState } from "react";
import { CaptureHeader } from "./components/CaptureHeader";
import { CaptureTabs, capturePanelId, type CaptureSubject } from "./components/CaptureTabs";
import { PopupShell } from "./components/PopupChrome";
import { useExtensionSession } from "./hooks/useExtensionSession";
import { useActivePage } from "./hooks/useActivePage";
import { useCaptureSettings } from "./hooks/useCaptureSettings";
import { CaptureCompanyScreen } from "./screens/CaptureCompanyScreen";
import { CapturePersonScreen } from "./screens/CapturePersonScreen";
import { CaptureSettingsScreen } from "./screens/CaptureSettingsScreen";
import { SignedOutScreen } from "./screens/SignedOutScreen";

/**
 * The popup's root: which of the states the consultant is in.
 *
 * Pairing decides everything above the tabs — an unpaired extension shows nothing about a mandate,
 * because it does not know which workspace it would belong to.
 *
 * The page is read here rather than inside a tab, so one injection feeds both and switching tabs
 * costs nothing.
 */
export function CapturePopup() {
  const session = useExtensionSession();
  const page = useActivePage();
  const { settings } = useCaptureSettings();
  const [subject, setSubject] = useState<CaptureSubject>("company");
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const hasAutoSelected = useRef(false);

  // Preselects the tab from what the page turned out to be, once and only once: a re-scan must not
  // pull the tab out from under someone who has started editing the other one. "unknown" leaves the
  // choice alone rather than guessing.
  useEffect(() => {
    if (hasAutoSelected.current || !settings.isPageTypeDetected || !page.subject || page.subject === "unknown") {
      return;
    }
    hasAutoSelected.current = true;
    setSubject(page.subject);
  }, [page.subject, settings.isPageTypeDetected]);

  if (session.isLoading) {
    return (
      <PopupShell>
        <div className="flex flex-1 items-center justify-center text-[12.5px] text-text3">Loading…</div>
      </PopupShell>
    );
  }

  if (!session.isPaired) {
    return <SignedOutScreen onConnected={() => void session.refresh()} />;
  }

  if (isSettingsOpen) {
    return (
      <PopupShell>
        <CaptureSettingsScreen
          user={session.user}
          onBack={() => setIsSettingsOpen(false)}
          onSignOut={() => void session.signOut()}
        />
      </PopupShell>
    );
  }

  return (
    <PopupShell>
      <CaptureHeader
        user={session.user}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onSignOut={() => void session.signOut()}
      />
      <CaptureTabs active={subject} onSelect={setSubject} />
      <div role="tabpanel" id={capturePanelId(subject)} aria-labelledby={`capture-tab-${subject}`}>
        {subject === "company" ? <CaptureCompanyScreen page={page} /> : <CapturePersonScreen page={page} />}
      </div>
    </PopupShell>
  );
}
