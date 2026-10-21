import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import type { ConnectedMailbox } from "../api/mailboxApi";
import { providerLabel, type MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";

/**
 * The line under the Outreach page's tabs that says what the consultant's mailbox needs before outreach can go from
 * it — connecting, reconnecting, or moving off the old connection. Nothing once it is ready.
 */
export function MailboxBanner({ state, connector }: { state: MailboxState; connector: MailboxConnect }) {
  const { connectingProvider, connect } = connector;
  switch (state.kind) {
    case "loading":
    case "refused":
    case "ready":
      return null;
    case "unavailable":
      return (
        <p className="rounded-u-card border border-u-border bg-u-raised px-5 py-4 text-body text-u-text2">
          Outreach email isn't switched on for your workspace. Contact Uncava support to turn it on.
        </p>
      );
    case "none":
      return (
        <div className="flex flex-wrap items-center gap-4 rounded-u-card border border-u-border-strong bg-u-raised px-5 py-4">
          <div className="grid size-10 flex-none place-items-center rounded-u-card bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.mail} size={20} />
          </div>
          <div className="min-w-0 flex-1 basis-64">
            <div className="text-body font-semibold">Connect your mailbox to send and receive email from Uncava</div>
            <div className="mt-1 text-note text-u-text2">
              Emails go from your own address and replies land in your own inbox. A reply stops its sequence, and
              Uncava keeps the emails you send from here and the replies to them, for your team only.
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {state.providers.map((provider) => (
              <Button
                key={provider}
                variant="secondary"
                size="sm"
                loading={connectingProvider === provider}
                disabled={connectingProvider !== null}
                onClick={() => connect(provider)}
              >
                Connect {providerLabel(provider)}
              </Button>
            ))}
          </div>
        </div>
      );
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
