import { useState } from "react";
import { CaptureHeader } from "./components/CaptureHeader";
import { CaptureTabs, type CaptureSubject } from "./components/CaptureTabs";
import { PopupShell } from "./components/PopupChrome";
import { useExtensionSession } from "./hooks/useExtensionSession";
import { CaptureCompanyScreen } from "./screens/CaptureCompanyScreen";
import { PersonCaptureComingSoonScreen } from "./screens/PersonCaptureComingSoonScreen";
import { SignedOutScreen } from "./screens/SignedOutScreen";

/**
 * The popup's root: which of the states the consultant is in.
 *
 * Pairing decides everything above the tabs — an unpaired extension shows nothing about a mandate,
 * because it does not know which workspace it would belong to. Below the tabs, Company is the one
 * that captures; Person says what it is waiting for.
 */
export function CapturePopup() {
  const session = useExtensionSession();
  const [subject, setSubject] = useState<CaptureSubject>("company");

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

  return (
    <PopupShell>
      <CaptureHeader user={session.user} onSignOut={() => void session.signOut()} />
      <CaptureTabs active={subject} onSelect={setSubject} />
      {subject === "company" ? <CaptureCompanyScreen /> : <PersonCaptureComingSoonScreen />}
    </PopupShell>
  );
}
