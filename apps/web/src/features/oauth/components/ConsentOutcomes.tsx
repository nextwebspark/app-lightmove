import { Link } from "react-router-dom";
import { grantedSummary, refusalMessage, type ConsentOutcome } from "../lib/consentRequest";

/** What the screen says once the server has answered. */
export function OutcomeView({ outcome, clientName }: { outcome: ConsentOutcome; clientName: string }) {
  switch (outcome.kind) {
    case "allowed":
      return (
        <div className="text-center">
          <span className="mx-auto grid size-10 place-items-center rounded-full bg-u-direct-tint text-u-direct">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <h1 className="mt-4 text-lg font-semibold text-u-text">{clientName} is connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            It can now read {grantedSummary(outcome.scopes)} in {outcome.workspaceName}. You can close this window.
          </p>
          <p className="mt-3.5 text-xs text-u-text3">
            It appears in Settings → Connected AI apps, where you can disconnect it at any time.
          </p>
        </div>
      );
    case "denied":
      return (
        <div className="text-center">
          <h1 className="text-lg font-semibold text-u-text">Not connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            {clientName} was told no and can read nothing. You can close this window.
          </p>
        </div>
      );
    case "notConnected":
      return (
        <div className="text-center">
          <h1 className="text-lg font-semibold text-u-text">Not connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            Uncava couldn't connect {clientName}, and it can read nothing. Start the connection again from the app.
          </p>
        </div>
      );
    case "error":
      return <ErrorOutcome code={outcome.code} />;
  }
}

export function RefusedOutcome({ clientName, workspaceName }: { clientName: string; workspaceName: string }) {
  return (
    <div className="text-center">
      <h1 className="text-lg font-semibold text-u-text">AI apps are for your firm's staff</h1>
      <p className="mt-2 text-sm leading-relaxed text-u-text2">
        You're signed in as a client contact of {workspaceName}, so {clientName} can't connect on your behalf. The
        positions shared with you stay open in Uncava as usual.
      </p>
      <OutcomeLink />
    </div>
  );
}

/** An unverified account connects nothing; the server refuses it too. */
export function VerifyEmailFirst() {
  return (
    <div className="text-center">
      <h1 className="text-lg font-semibold text-u-text">Verify your email first</h1>
      <p className="mt-2 text-sm leading-relaxed text-u-text2">
        An AI app can connect only once your email address is verified. Follow the link we sent you, then start the
        connection again from the app.
      </p>
      <OutcomeLink to="/signup/verify-email" label="Resend the link" />
    </div>
  );
}

export function ErrorOutcome({ code }: { code: string }) {
  return (
    <div className="text-center">
      <h1 className="text-lg font-semibold text-u-text">This connection can't go ahead</h1>
      <p role="alert" className="mt-2 text-sm leading-relaxed text-u-offlimits">
        {refusalMessage(code)}
      </p>
      <p className="mt-3.5 text-xs text-u-text3">
        Nothing was shared, and you were not sent anywhere. Start the connection again from the app.
      </p>
    </div>
  );
}

function OutcomeLink({ to = "/", label = "Back to Uncava" }: { to?: string; label?: string }) {
  return (
    <Link
      to={to}
      className="mt-5 inline-flex items-center justify-center rounded-[6px] border border-u-border-strong bg-u-surface px-3.5 py-2.5 text-[13.5px] font-medium text-u-text2 hover:text-u-text"
    >
      {label}
    </Link>
  );
}
