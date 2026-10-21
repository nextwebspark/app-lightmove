import type { ReactNode } from "react";
import { Button } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { providerLabel, type MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { MailboxProviderMark } from "./MailboxProviderMark";

/** How an action that needs a mailbox looks while there is none to send from: present, quiet, not the call to make. */
export const WAITING_ON_MAILBOX =
  "inline-flex items-center gap-1.5 rounded-u-control border border-dashed border-u-border-strong px-3 py-1.5 text-note font-medium text-u-text3 hover:text-u-text2";

/**
 * An action that needs the consultant's mailbox while it cannot send: drawn quiet, it says why on hover, and a click
 * offers the connection instead of doing nothing.
 */
export function ConnectFirstButton({
  state,
  connector,
  triggerClassName = WAITING_ON_MAILBOX,
  align = "right",
  children,
}: {
  state: Extract<MailboxState, { kind: "none" } | { kind: "error" }>;
  connector: MailboxConnect;
  triggerClassName?: string;
  align?: "left" | "right";
  children: ReactNode;
}) {
  const reason = state.kind === "error" ? "Reconnect your mailbox first" : "Connect your mailbox first";
  const providers = state.kind === "error" ? [state.connection.provider] : state.providers;
  return (
    <Popover align={align} width={260} hasPopup="dialog" title={reason} triggerClassName={triggerClassName} trigger={() => children}>
      {(close) => (
        <div role="group" aria-label={reason} className="flex flex-col gap-2 p-1">
          <p className="text-note font-semibold text-u-text">{reason}</p>
          <p className="text-meta text-u-text3">Emails go from your own address, and replies come back here.</p>
          {providers.map((provider) => (
            <Button
              key={provider}
              variant="secondary"
              size="xs"
              className="w-full"
              loading={connector.connectingProvider === provider}
              disabled={connector.connectingProvider !== null}
              onClick={() => {
                close();
                connector.connect(provider);
              }}
            >
              <MailboxProviderMark provider={provider} size={13} />
              {state.kind === "error" ? `Reconnect ${providerLabel(provider)}` : `Connect ${providerLabel(provider)}`}
            </Button>
          ))}
        </div>
      )}
    </Popover>
  );
}

/** Whether an action that sends must wait for the mailbox, and on what; null where it can go ahead. */
export function mailboxBlockerOf(
  state: MailboxState,
): Extract<MailboxState, { kind: "none" } | { kind: "error" }> | null {
  return state.kind === "none" || state.kind === "error" ? state : null;
}
