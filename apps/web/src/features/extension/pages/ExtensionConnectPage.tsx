import { useCallback, useEffect, useRef, useState } from "react";
import { Button, Card, Logo, Spinner } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as extensionApi from "../api/extensionApi";

/**
 * Where the browser extension is paired with this account.
 *
 * The extension cannot use this app's session cookie — it is `SameSite=Strict`, host-only and scoped
 * to `/api/v1/auth`, and reaching it from a `chrome-extension://` origin would mean stripping every
 * attribute that protects it. So this page, which *is* on the right origin and *is* signed in, asks
 * the API for a refresh token of the extension's own and hands it over.
 *
 * **The handover is addressed to one extension, not broadcast.** `chrome.runtime.sendMessage(id, …)`
 * delivers to exactly the extension named. The obvious-looking alternative — `window.postMessage` to
 * this page's own window — is not private: every listener in the frame receives it, and a content
 * script's isolated world does not isolate it from those events, so any other extension the consultant
 * had installed with a broad content script would have read the refresh token straight off this page.
 *
 * Naming the id here costs nothing. It is not a secret, it is pinned by the extension's manifest key,
 * and `application.yml` already names the same id in the CORS allow-list — so it was never the
 * "hardcoded id" the postMessage version claimed to avoid.
 */

/**
 * The extension this page hands the session to.
 *
 * Configurable because it has to be: the id below is the one the pinned manifest key produces when the
 * extension is **loaded unpacked**, which is right for development and wrong for anything published —
 * the Chrome Web Store assigns its own id when the item is created. Set `VITE_EXTENSION_ID` at build
 * time to whatever the store assigned.
 *
 * It must stay in step with the API's `lightmove.web.cors-allowed-origins`, which allow-lists the
 * matching `chrome-extension://` origin. Two places, one value; the extension README says how.
 */
const DEVELOPMENT_EXTENSION_ID = "kllpamcdcnecpdblgdkehgbhdjdlbofh";

const EXTENSION_ID = import.meta.env.VITE_EXTENSION_ID || DEVELOPMENT_EXTENSION_ID;

/** Shared verbatim with the extension's service worker — the one contract between them. */
const STORE_PAIRED_SESSION = "storePairedSession";

type ConnectState = "pairing" | "paired" | "notInstalled" | "failed";

/** What `chrome.runtime` looks like from a web page: present only when an extension exposes it. */
interface PageAccessibleChromeRuntime {
  sendMessage: (
    extensionId: string,
    message: unknown,
    callback: (response?: { ok?: boolean; message?: string }) => void,
  ) => void;
  lastError?: { message?: string };
}

function extensionChannel(): PageAccessibleChromeRuntime | null {
  const runtime = (window as { chrome?: { runtime?: PageAccessibleChromeRuntime } }).chrome?.runtime;
  return typeof runtime?.sendMessage === "function" ? runtime : null;
}

export function ExtensionConnectPage() {
  const [state, setState] = useState<ConnectState>("pairing");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hasStarted = useRef(false);

  const pair = useCallback(async () => {
    const runtime = extensionChannel();
    if (!runtime) {
      // Checked before minting, deliberately: opening this URL without the extension installed must
      // not leave a live refresh token in a page that has nothing to collect it.
      setState("notInstalled");
      return;
    }

    setState("pairing");
    try {
      const session = await extensionApi.pairExtension();
      runtime.sendMessage(EXTENSION_ID, { kind: STORE_PAIRED_SESSION, session }, (response) => {
        // `lastError` has to be read or Chrome logs an unchecked-error warning; it is also the only
        // signal that the id named above resolves to nothing installed.
        if (runtime.lastError || !response?.ok) {
          setState("notInstalled");
          return;
        }
        setState("paired");
      });
    } catch (error) {
      setState("failed");
      setErrorMessage(
        error instanceof ApiRequestError
          ? error.problem.detail
          : "The workspace could not create a session for the extension.",
      );
    }
  }, []);

  useEffect(() => {
    // Once per mount. A second run would mint a second token and abandon the first as a live
    // credential nobody holds.
    if (hasStarted.current) {
      return;
    }
    hasStarted.current = true;
    void pair();
  }, [pair]);

  const retry = () => {
    hasStarted.current = false;
    void pair();
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg p-6">
      <Card className="w-full max-w-md text-center">
        <div className="flex justify-center">
          <Logo />
        </div>
        <ConnectStatus state={state} errorMessage={errorMessage} onRetry={retry} />
      </Card>
    </div>
  );
}

function ConnectStatus({
  state,
  errorMessage,
  onRetry,
}: {
  state: ConnectState;
  errorMessage: string | null;
  onRetry: () => void;
}) {
  if (state === "paired") {
    return (
      <>
        <h1 className="mt-6 text-lg font-semibold text-text">LightMove Capture is connected</h1>
        <p className="mt-2 text-sm leading-relaxed text-text2">
          You can close this tab. Open the extension from your toolbar, or press ⌥⇧L, on any company
          page to capture it into a mandate.
        </p>
        <p className="mt-4 text-xs text-text3">
          It appears in Settings → Active sessions as <span className="font-medium">LightMove Capture</span>,
          and you can end it there at any time without signing out of this browser.
        </p>
      </>
    );
  }

  if (state === "notInstalled") {
    return (
      <>
        <h1 className="mt-6 text-lg font-semibold text-text">Extension not detected</h1>
        <p className="mt-2 text-sm leading-relaxed text-text2">
          This page could not reach LightMove Capture. Install it, make sure it is enabled at
          <span className="font-mono"> chrome://extensions</span>, then try again.
        </p>
        <Button className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      </>
    );
  }

  if (state === "failed") {
    return (
      <>
        <h1 className="mt-6 text-lg font-semibold text-text">Could not connect the extension</h1>
        <p role="alert" className="mt-2 text-sm leading-relaxed text-red">
          {errorMessage}
        </p>
        <Button className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      </>
    );
  }

  return (
    <>
      <h1 className="mt-6 text-lg font-semibold text-text">Connecting LightMove Capture…</h1>
      <p className="mt-2 text-sm leading-relaxed text-text2">Handing the session to the extension.</p>
      <div className="mt-5 flex justify-center">
        <Spinner />
      </div>
    </>
  );
}
