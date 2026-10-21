import { Button } from "../../../components/ui";
import type { ConnectedMailbox } from "../api/mailboxApi";
import { providerLabel, type MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";

/**
 * The line under the Outreach header that says what a connected mailbox needs before outreach can go on from it —
 * reconnecting, or moving off the old connection. Nothing while it is ready; connecting is the setup card's.
 */
export function MailboxBanner({ state, connector }: { state: MailboxState; connector: MailboxConnect }) {
  const strip = stripOf(state, connector);
  if (!strip) return null;
  return <div className="px-4 pt-3 md:px-6">{strip}</div>;
}

function stripOf(state: MailboxState, connector: MailboxConnect) {
  switch (state.kind) {
    case "loading":
    case "refused":
    case "unavailable":
    case "none":
    case "ready":
      return null;
    case "error":
      return (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-u-chip bg-u-offlimits-tint px-3.5 py-2.5 text-body text-u-text"
        >
          <span className="min-w-0 flex-1 basis-64">
            <b className="text-u-offlimits">{providerLabel(state.connection.provider)} disconnected.</b> Access to{" "}
            {state.connection.address} was withdrawn. Reconnect to send from it again.
          </span>
          <ReconnectButton connection={state.connection} connector={connector} />
        </div>
      );
    case "movesOffNylas": {
      const runs = state.connection.runsStoppedByMove;
      return (
        <div className="flex flex-wrap items-center gap-3 rounded-u-chip border border-u-border bg-u-raised px-3.5 py-2.5 text-body text-u-text">
          <span className="min-w-0 flex-1 basis-64">
            <b>Reconnect your mailbox.</b> Uncava now connects to {providerLabel(state.connection.provider)} directly.
            Reconnect {state.connection.address} once to keep sending from it.
            {runs > 0 && (
              <>
                {" "}
                <span className="text-u-offlimits">
                  {runs === 1 ? "1 running sequence stops" : `${runs} running sequences stop`} at{" "}
                  {runs === 1 ? "its" : "their"} next email; start {runs === 1 ? "it" : "them"} again afterwards.
                </span>
              </>
            )}
            {state.connection.bookingLink && <> Your booking link stops working, so sequences that send it stop too.</>}
          </span>
          <ReconnectButton connection={state.connection} connector={connector} />
        </div>
      );
    }
    case "readScopeMissing":
      return (
        <div className="flex flex-wrap items-center gap-3 rounded-u-chip border border-u-border bg-u-raised px-3.5 py-2.5 text-body text-u-text">
          <span className="min-w-0 flex-1 basis-64">
            <b>Reconnect to show replies here.</b> Reconnect {state.connection.address} so Uncava can show what people
            reply. Until then a reply still stops its sequence.
          </span>
          <ReconnectButton connection={state.connection} connector={connector} />
        </div>
      );
  }
}

function ReconnectButton({ connection, connector }: { connection: ConnectedMailbox; connector: MailboxConnect }) {
  return (
    <Button
      variant="secondary"
      size="xs"
      className="ms-auto"
      loading={connector.connectingProvider === connection.provider}
      onClick={() => connector.connect(connection.provider)}
    >
      Reconnect
    </Button>
  );
}
