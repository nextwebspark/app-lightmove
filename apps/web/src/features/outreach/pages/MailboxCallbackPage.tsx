import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Logo } from "../../../components/ui";
import {
  type MailboxOutcome,
  reportMailboxOutcome,
  takeMailboxHandshake,
  takeMailboxReturnTo,
} from "../lib/mailboxPopup";

/**
 * Where the server sends the browser once the mailbox provider has answered. The server has already
 * stored the mailbox (or refused it), so in the connect popup this page only reports the outcome and
 * closes; after a full-page connection it returns to wherever the consultant started.
 */
export function MailboxCallbackPage() {
  const navigate = useNavigate();
  const isHandled = useRef(false);

  useEffect(() => {
    if (isHandled.current) {
      return;
    }
    isHandled.current = true;

    const handshakeId = takeMailboxHandshake();
    if (handshakeId) {
      reportMailboxOutcome(handshakeId, outcomeOf(new URLSearchParams(window.location.search)));
      window.close();
      return;
    }
    navigate(takeMailboxReturnTo() ?? "/", { replace: true });
  }, [navigate]);

  return (
    <div className="grid min-h-screen place-items-center bg-u-bg px-4 text-center">
      <div>
        <Logo />
        <p className="mt-4 text-[13px] text-u-text2">Connecting your mailbox… you can close this window.</p>
      </div>
    </div>
  );
}

function outcomeOf(query: URLSearchParams): MailboxOutcome {
  if (query.get("status") === "connected") {
    return { status: "connected" };
  }
  return { status: "error", code: query.get("error") ?? "MAILBOX_CONNECT_FAILED" };
}
