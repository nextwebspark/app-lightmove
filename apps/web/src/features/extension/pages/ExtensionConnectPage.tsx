import { useCallback, useEffect, useRef, useState } from "react";
import { Button, Card, Logo, Spinner } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as extensionApi from "../api/extensionApi";

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

/** Asked before anything is minted, purely to find out whether the extension answers at all. */
const PING = "ping";

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

/** Whether LightMove Capture is installed and listening, asked with a message that changes nothing. */
function extensionAnswers(runtime: PageAccessibleChromeRuntime): Promise<boolean> {
  return new Promise((resolve) => {
    try {
      runtime.sendMessage(EXTENSION_ID, { kind: PING }, (response) => {
        resolve(!runtime.lastError && Boolean(response));
      });
    } catch {
      resolve(false);
    }
  });
}

export function ExtensionConnectPage() {
  const [state, setState] = useState<ConnectState>("pairing");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const isPairing = useRef(false);

  const pair = useCallback(async () => {
    // Guards a double-click as well as a re-render: each run ends the extension session the account
    // holds and mints a replacement, so a second run mid-handover signs the extension out again.
    if (isPairing.current) {
      return;
    }
    isPairing.current = true;

    const runtime = extensionChannel();
    // `chrome.runtime.sendMessage` exists in Chrome whether or not *this* extension is installed, so
    // its presence proves nothing. Minting before knowing would leave a live 14-day token in a page
    // with nothing to collect it — and, now that pairing revokes the account's previous extension
    // session, would sign out an extension on another machine to hand the token to nobody.
    if (!runtime || !(await extensionAnswers(runtime))) {
      setState("notInstalled");
      isPairing.current = false;
      return;
    }

    setState("pairing");
    try {
      const session = await extensionApi.pairExtension();
      // Addressed to one extension, never `window.postMessage`: that reaches every listener in the
      // frame, and a content script's isolated world does not isolate it from those events — so any
      // other extension with a broad content script would read this refresh token off the page.
      runtime.sendMessage(EXTENSION_ID, { kind: STORE_PAIRED_SESSION, session }, (response) => {
        // `lastError` has to be read or Chrome logs an unchecked-error warning; it is also the only
        // signal that the id named above resolves to nothing installed.
        isPairing.current = false;
        if (runtime.lastError || !response?.ok) {
          setState("notInstalled");
          return;
        }
        setState("paired");
      });
    } catch (error) {
      isPairing.current = false;
      setState("failed");
      setErrorMessage(refusalMessage(error));
    }
  }, []);

  // The route is reached only from the extension's own button, by a consultant already signed in —
  // so asking them to click "Connect" again was a step that told them nothing. Pairing runs on
  // arrival instead; the buttons below remain for the states a retry can fix. The ref inside `pair`
  // is what makes this safe to fire from an effect that runs twice under StrictMode.
  useEffect(() => {
    void pair();
  }, [pair]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg p-6">
      <Card className="w-full max-w-md text-center">
        <div className="flex justify-center">
          <Logo />
        </div>
        <ConnectStatus state={state} errorMessage={errorMessage} onConnect={() => void pair()} />
      </Card>
    </div>
  );
}

function ConnectStatus({
  state,
  errorMessage,
  onConnect,
}: {
  state: ConnectState;
  errorMessage: string | null;
  onConnect: () => void;
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
        <Button className="mt-5" onClick={onConnect}>
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
        <Button className="mt-5" onClick={onConnect}>
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

/**
 * What to tell the consultant, keyed on `code` rather than `detail`.
 *
 * The auth surface's own messages are deliberately vague, so relaying one says nothing useful. Only
 * the rate limit is worth naming: it is the single refusal here a consultant can act on.
 */
function refusalMessage(error: unknown): string {
  if (error instanceof ApiRequestError && error.code === "RATE_LIMITED") {
    return "Too many attempts to connect the extension. Try again in an hour.";
  }
  return "The workspace could not create a session for the extension.";
}
