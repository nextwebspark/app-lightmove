import { workspaceOrigin } from "../../workspaceOrigin";
import { BrandTile } from "../components/PopupChrome";
import { PopupShell } from "../components/PopupChrome";

interface SignedOutScreenProps {
  onConnected: () => void;
}

/**
 * Before the extension is paired.
 *
 * There is no login form here, and there must not be: asking for a password in a popup trains
 * consultants to type their workspace credentials into whatever surface asks for them. The button
 * opens the workspace in a tab, where they are already signed in or can sign in against the real
 * origin with the real certificate, and the connect page hands the session back.
 */
export function SignedOutScreen({ onConnected }: SignedOutScreenProps) {
  const connectUrl = `${workspaceOrigin}/extension/connect`;

  const handleConnect = () => {
    chrome.tabs.create({ url: connectUrl });
    // The popup closes the moment the new tab takes focus, so the pairing is picked up on the next
    // open rather than watched for here. Re-checking first covers the case where it does survive.
    onConnected();
  };

  return (
    <PopupShell>
      <header className="flex items-center gap-[9px] border-b border-line-soft px-3.5 py-[11px]">
        <BrandTile />
        <span className="font-mono text-[13px] font-semibold tracking-[0.02em]">LightMove Capture</span>
      </header>

      <div className="flex flex-1 flex-col items-center justify-center px-3.5 text-center">
        <span
          className="grid h-11 w-11 place-items-center rounded-xl border border-line bg-panel2 text-[20px] text-text3"
          aria-hidden
        >
          🔒
        </span>
        <h1 className="mt-3.5 text-[15px] font-semibold">Connect to LightMove</h1>
        <p className="mt-2 max-w-[280px] text-[12.5px] leading-[1.6] text-text2">
          Open your workspace to link this extension to your account. You will not need to sign in
          again here.
        </p>
        <button
          type="button"
          onClick={handleConnect}
          className="mt-4 inline-flex items-center gap-2 rounded-lg bg-amber-btn px-4 py-[9px] text-[13px] font-semibold text-on-amber"
        >
          Open LightMove
          <span aria-hidden>↗</span>
        </button>
        <p className="mt-2.5 font-mono text-[11px] text-text3">{connectUrl}</p>
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
