import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as mailboxApi from "../api/mailboxApi";
import type { ConnectedMailbox } from "../api/mailboxApi";
import { providerLabel, type MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { MailboxProviderMark } from "./MailboxProviderMark";
import { ZoomConnectControl } from "./ZoomConnectControl";

/** Whether {@link MailboxChip} draws anything for this state, so the header knows to divide it from the action. */
export function mailboxChipShows(
  state: MailboxState,
): state is Exclude<MailboxState, { kind: "loading" | "refused" | "unavailable" }> {
  return state.kind !== "loading" && state.kind !== "refused" && state.kind !== "unavailable";
}

/**
 * The consultant's own mailbox in the Outreach header: which address sends and how much of today's allowance it has
 * used, with its settings — a test email, Zoom and Disconnect — one click away.
 */
export function MailboxChip({
  state,
  connector,
  onAsked,
}: {
  state: MailboxState;
  connector: MailboxConnect;
  /** The mailbox is read again before Disconnect asks: runs and scheduled emails end without this page knowing. */
  onAsked: () => void;
}) {
  if (!mailboxChipShows(state)) return null;
  switch (state.kind) {
    case "none":
      return state.providers.length === 1 ? (
        <button
          type="button"
          className={NOT_CONNECTED_CHIP}
          disabled={connector.connectingProvider !== null}
          onClick={() => connector.connect(state.providers[0])}
        >
          <NotConnectedLabel />
        </button>
      ) : (
        <Popover align="right" width={220} hasPopup="menu" triggerClassName={NOT_CONNECTED_CHIP} trigger={() => <NotConnectedLabel />}>
          {(close) => (
            <div role="menu" className="flex flex-col p-1.5">
              {state.providers.map((provider) => (
                <button
                  key={provider}
                  type="button"
                  role="menuitem"
                  className="flex items-center gap-2.5 rounded-u-control px-2.5 py-2 text-start text-body text-u-text2 hover:bg-u-raised hover:text-u-text"
                  onClick={() => {
                    close();
                    connector.connect(provider);
                  }}
                >
                  <MailboxProviderMark provider={provider} />
                  Connect {providerLabel(provider)}
                </button>
              ))}
            </div>
          )}
        </Popover>
      );
    case "error":
    case "movesOffNylas":
    case "readScopeMissing":
    case "ready":
      return (
        <ConnectedChip
          connection={state.connection}
          isWithdrawn={state.kind === "error"}
          connector={connector}
          onAsked={onAsked}
        />
      );
  }
}

const NOT_CONNECTED_CHIP =
  "inline-flex h-[34px] items-center gap-2 rounded-u-control border border-u-border-strong px-3 text-note font-medium text-u-text2 hover:bg-u-raised hover:text-u-text disabled:opacity-50";

function NotConnectedLabel() {
  return (
    <>
      <span aria-hidden="true" className="size-1.5 flex-none rounded-full border border-u-text3" />
      Connect mailbox
    </>
  );
}

function ConnectedChip({
  connection,
  isWithdrawn,
  connector,
  onAsked,
}: {
  connection: ConnectedMailbox;
  isWithdrawn: boolean;
  connector: MailboxConnect;
  onAsked: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isConfirmingDisconnect, setIsConfirmingDisconnect] = useState(false);
  const sendTest = useMutation({
    mutationFn: mailboxApi.sendMailboxTest,
    onSuccess: () => toast("Test email sent. Check your inbox."),
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    },
  });
  const disconnect = useMutation({
    mutationFn: mailboxApi.disconnectMailbox,
    onSuccess: () => {
      setIsConfirmingDisconnect(false);
      toast("Mailbox disconnected.");
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <>
      <Popover
        align="right"
        width={300}
        hasPopup="dialog"
        label={`Mailbox ${connection.address}`}
        triggerClassName="inline-flex h-[34px] min-w-0 max-w-full items-center gap-2 rounded-u-control border border-u-border-strong px-3 font-sans text-note text-u-text transition hover:bg-u-raised"
        trigger={() => (
          <>
            <span
              aria-hidden="true"
              className={cn("size-1.5 flex-none rounded-full", isWithdrawn ? "bg-u-offlimits" : "bg-u-direct")}
            />
            <span className="min-w-0 truncate">{connection.address}</span>
            <span className="hidden flex-none text-u-text3 md:inline">
              {connection.sentToday} of {connection.dailyCap} today
            </span>
          </>
        )}
      >
        {(close) => (
          <div className="flex flex-col gap-3 p-3.5">
            <div>
              <div className="truncate font-mono text-note text-u-text">{connection.address}</div>
              <div className="mt-0.5 text-meta text-u-text3">
                {isWithdrawn
                  ? `${providerLabel(connection.provider)} withdrew access. Reconnect to send again.`
                  : `${connection.sentToday} of ${connection.dailyCap} sent today. Replies land in your own inbox.`}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {isWithdrawn ? (
                <Button
                  variant="secondary"
                  size="xs"
                  loading={connector.connectingProvider === connection.provider}
                  onClick={() => {
                    close();
                    connector.connect(connection.provider);
                  }}
                >
                  Reconnect
                </Button>
              ) : (
                <Button variant="secondary" size="xs" loading={sendTest.isPending} onClick={() => sendTest.mutate()}>
                  Send a test email
                </Button>
              )}
              <Button
                variant="ghost"
                size="xs"
                loading={disconnect.isPending}
                onClick={() => {
                  close();
                  onAsked();
                  setIsConfirmingDisconnect(true);
                }}
              >
                Disconnect
              </Button>
            </div>
            {!isWithdrawn && <ZoomConnectControl />}
          </div>
        )}
      </Popover>
      <ConfirmDialog
        open={isConfirmingDisconnect}
        title={`Disconnect ${connection.address}?`}
        confirmLabel="Disconnect"
        pending={disconnect.isPending}
        onConfirm={() => disconnect.mutate()}
        onClose={() => setIsConfirmingDisconnect(false)}
      >
        {(connection.liveSequences > 0 || connection.scheduledEmails > 0) && (
          <p>
            <strong className="font-semibold text-u-text">{stoppedLine(connection)}</strong>
          </p>
        )}
        <p>You can reconnect later, but sequences that stop don&rsquo;t restart.</p>
      </ConfirmDialog>
    </>
  );
}

/** "2 live sequences (5 people) will stop sending, and 1 scheduled email won't go unless you reconnect before it is due." */
function stoppedLine({ liveSequences, livePeople, scheduledEmails }: ConnectedMailbox): string {
  const parts: string[] = [];
  if (liveSequences > 0) {
    parts.push(
      `${liveSequences} live ${liveSequences === 1 ? "sequence" : "sequences"} ` +
        `(${livePeople} ${livePeople === 1 ? "person" : "people"}) will stop sending`,
    );
  }
  if (scheduledEmails > 0) {
    parts.push(
      `${scheduledEmails} scheduled ${scheduledEmails === 1 ? "email" : "emails"} won't go unless you reconnect before ${scheduledEmails === 1 ? "it is" : "they are"} due`,
    );
  }
  return `${parts.join(", and ")}.`;
}
