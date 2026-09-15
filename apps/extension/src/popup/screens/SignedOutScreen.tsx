import { askServiceWorker } from "../../background/extensionMessages";
import { extensionConnectUrl } from "../../workspaceOrigin";
import { BrandMark, PopupShell } from "../components/PopupChrome";
import { Icon } from "../components/Icon";
import { ICONS } from "../lib/icons";

interface SignedOutScreenProps {
  onConnected: () => void;
}

/**
 * Before the extension is paired. No login form here, and there must not be: a password box in a popup
 * trains consultants to type workspace credentials into whatever asks. The button opens the real
 * origin instead.
 */
export function SignedOutScreen({ onConnected }: SignedOutScreenProps) {
  const handleConnect = () => {
    // The worker opens it, so it can remember which tab to return to when the page closes itself.
    void askServiceWorker({ kind: "openConnectPage" });
    // The panel stays open while the consultant pairs in that tab, and `useExtensionSession` watches
    // the store for the session arriving — so this is only the immediate re-check, not the mechanism.
    onConnected();
  };

  return (
    <PopupShell>
      <header className="flex items-center gap-[9px] border-b border-line-soft px-3.5 py-[11px]">
        <BrandMark />
        <span className="font-mono text-[13px] font-semibold tracking-[0.02em]">UNCAVA Capture</span>
      </header>

      <div className="flex flex-1 flex-col items-center justify-center px-3.5 text-center">
        <BrandMark className="h-14" />
        <h1 className="mt-4 text-[15px] font-semibold">Connect to Uncava</h1>
        <p className="mt-2 max-w-[280px] text-[12.5px] leading-[1.6] text-text2">
          Open your workspace to link this extension to your account. You will not need to sign in
          again here.
        </p>
        <button
          type="button"
          onClick={handleConnect}
          className="mt-4 inline-flex items-center gap-2 rounded-lg bg-amber-btn px-4 py-[9px] text-[13px] font-semibold text-on-amber"
        >
          Open Uncava
          <Icon d={ICONS.externalLink} />
        </button>
        <p className="mt-2.5 font-mono text-[11px] text-text3">{extensionConnectUrl}</p>
      </div>

      <footer className="flex items-center justify-between border-t border-line-soft px-3.5 py-2.5 font-mono text-[11px] text-text3">
        <span>Not connected</span>
        <button type="button" onClick={onConnected} className="text-sky hover:underline">
          Already connected? Recheck
        </button>
      </footer>
    </PopupShell>
  );
}
