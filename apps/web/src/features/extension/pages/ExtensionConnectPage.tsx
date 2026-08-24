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
 * The handover is a `postMessage` to a content script the extension injects on this exact URL. Two
 * things follow from that, and both are deliberate:
 *
 *  - **The extension's id is never hardcoded here.** `chrome.runtime.sendMessage(id, …)` would need
 *    one; a content script the extension itself chose to inject does not.
 *  - **Nothing is minted until the extension has answered.** The bridge announces itself on
 *    injection, and this page waits for that before asking for a token — so opening this URL without
 *    the extension installed creates no credential at all, rather than leaving a live refresh token
 *    in a page nothing collected it from.
 */

/** Shared verbatim with `apps/extension/src/content/pairingBridge.ts`. The one contract between them. */
const EXTENSION_READY = "lightmove.extension.ready";
const SESSION_OFFERED = "lightmove.extension.session";
const SESSION_STORED = "lightmove.extension.paired";

/** How long to wait for the bridge before concluding the extension is not installed. */
const BRIDGE_TIMEOUT_MS = 2_000;

type ConnectState = "waitingForExtension" | "pairing" | "paired" | "notInstalled" | "failed";

export function ExtensionConnectPage() {
  const [state, setState] = useState<ConnectState>("waitingForExtension");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hasPaired = useRef(false);

  const pair = useCallback(async () => {
    // A second announcement from the bridge — it sends one per injection — must not mint a second
    // token. The first one is the session; the rest are noise.
    if (hasPaired.current) {
      return;
    }
    hasPaired.current = true;
    setState("pairing");

    try {
      const session = await extensionApi.pairExtension();
      window.postMessage({ type: SESSION_OFFERED, session }, window.location.origin);
    } catch (error) {
      hasPaired.current = false;
      setState("failed");
      setErrorMessage(
        error instanceof ApiRequestError
          ? error.problem.detail
          : "The workspace could not create a session for the extension.",
      );
    }
  }, []);

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      // Same two checks the extension's side makes, for the same reason: anything from another window
      // or another origin is not our content script and has no business in this exchange.
      if (event.source !== window || event.origin !== window.location.origin) {
        return;
      }
      const message = event.data as { type?: unknown; ok?: unknown } | null;

      if (message?.type === EXTENSION_READY) {
        void pair();
      }
      if (message?.type === SESSION_STORED) {
        setState(message.ok ? "paired" : "failed");
        if (!message.ok) {
          setErrorMessage("The extension did not accept the session. Reload this page and try again.");
        }
      }
    };

    window.addEventListener("message", onMessage);

    // The bridge announces itself on injection, but it may already have done so before this listener
    // existed — a content script at document_idle can beat React's first effect. So the page also
    // gives up after a moment and says the extension is not there, rather than spinning forever.
    const timeout = window.setTimeout(() => {
      setState((current) => (current === "waitingForExtension" ? "notInstalled" : current));
    }, BRIDGE_TIMEOUT_MS);

    return () => {
      window.removeEventListener("message", onMessage);
      window.clearTimeout(timeout);
    };
  }, [pair]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg p-6">
      <Card className="w-full max-w-md text-center">
        <div className="flex justify-center">
          <Logo />
        </div>
        <ConnectStatus state={state} errorMessage={errorMessage} />
      </Card>
    </div>
  );
}

function ConnectStatus({ state, errorMessage }: { state: ConnectState; errorMessage: string | null }) {
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
          This page did not hear from LightMove Capture. Install it, make sure it is enabled at
          <span className="font-mono"> chrome://extensions</span>, then reload this page.
        </p>
        <Button className="mt-5" onClick={() => window.location.reload()}>
          Reload and try again
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
        <Button className="mt-5" onClick={() => window.location.reload()}>
          Try again
        </Button>
      </>
    );
  }

  return (
    <>
      <h1 className="mt-6 text-lg font-semibold text-text">Connecting LightMove Capture…</h1>
      <p className="mt-2 text-sm leading-relaxed text-text2">
        {state === "pairing" ? "Handing the session to the extension." : "Looking for the extension."}
      </p>
      <div className="mt-5 flex justify-center">
        <Spinner />
      </div>
    </>
  );
}
