import { useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Button } from "../../../components/ui";
import { MailboxProviderMark } from "../../outreach/components/MailboxProviderMark";
import { mailboxStateOf, providerLabel } from "../../outreach/lib/mailboxState";
import { useMailbox } from "../../outreach/lib/useMailbox";
import { useMailboxConnect } from "../../outreach/lib/useMailboxConnect";
import { cn } from "../../../lib/cn";
import * as signaturesApi from "../api/signaturesApi";

/**
 * Where the signatures go: the mailbox Uncava sends from, or the way to connect one. A mailbox's own signature is
 * never added to email sent through an app, which is why Uncava draws its own.
 */
export function SignatureMailboxNote() {
  const queryClient = useQueryClient();
  const state = mailboxStateOf(useMailbox());
  // A new connection brings the booking link and the address every preview draws.
  const connector = useMailboxConnect({
    onConnected: () => void queryClient.invalidateQueries({ queryKey: signaturesApi.SIGNATURES_KEY }),
  });

  if (state.kind === "loading" || state.kind === "refused") return null;
  if (state.kind === "unavailable") {
    return (
      <Note>
        <span className="text-u-text2">
          Uncava adds your signature to the emails it sends for you. A mailbox doesn&rsquo;t add its own to email sent
          through an app.
        </span>
      </Note>
    );
  }

  if (state.kind === "none") {
    return (
      <Note
        actions={state.providers.map((provider) => (
          <Button
            key={provider}
            variant="secondary"
            size="sm"
            loading={connector.connectingProvider === provider}
            disabled={connector.connectingProvider !== null}
            onClick={() => connector.connect(provider)}
          >
            <MailboxProviderMark provider={provider} size={14} />
            Connect {providerLabel(provider)}
          </Button>
        ))}
      >
        <span className="block text-body font-medium text-u-text">Connect your mailbox to send with a signature</span>
        <span className="mt-0.5 block font-mono text-meta text-u-text3">
          Uncava sends from your own address and adds the signature itself.
        </span>
      </Note>
    );
  }

  const { connection } = state;
  const host = providerLabel(connection.provider);
  if (state.kind === "error") {
    return (
      <Note
        tone="warning"
        actions={
          <Button
            variant="secondary"
            size="sm"
            loading={connector.connectingProvider === connection.provider}
            disabled={connector.connectingProvider !== null}
            onClick={() => connector.connect(connection.provider)}
          >
            <MailboxProviderMark provider={connection.provider} size={14} />
            Reconnect {host}
          </Button>
        }
      >
        <span className="block text-body font-medium text-u-text">Reconnect {connection.address} to send</span>
        <span className="mt-0.5 block font-mono text-meta text-u-text3">
          {host} stopped letting Uncava send from it, so nothing goes until you reconnect.
        </span>
      </Note>
    );
  }

  return (
    <Note icon={<MailboxProviderMark provider={connection.provider} size={16} />}>
      <span className="block text-body text-u-text">
        Sending from <span className="font-medium">{connection.address}</span>
      </span>
      <span className="mt-0.5 block font-mono text-meta text-u-text3">
        Uncava adds the signature below. {host}&rsquo;s own signature isn&rsquo;t added to email sent through an app.
      </span>
    </Note>
  );
}

function Note({
  icon,
  actions,
  tone = "plain",
  children,
}: {
  icon?: ReactNode;
  actions?: ReactNode;
  tone?: "plain" | "warning";
  children: ReactNode;
}) {
  return (
    <div
      role="status"
      className={cn(
        "mb-4 flex flex-wrap items-center gap-x-4 gap-y-2.5 rounded-u-card border bg-u-surface px-4 py-3",
        tone === "warning" ? "border-u-offlimits" : "border-u-border",
      )}
    >
      {icon && <span className="flex shrink-0 items-center">{icon}</span>}
      <div className="min-w-0 flex-[1_1_260px] text-note">{children}</div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
