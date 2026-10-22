import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState } from "../../../components/ui";
import { providerLabel } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { MailboxProviderMark } from "./MailboxProviderMark";

/** Where the Outreach page's conversation goes while there is nobody to open and no mailbox is connected: one way in. */
export function MailboxSetupCard({
  providers,
  connector,
}: {
  providers: readonly string[];
  connector: MailboxConnect;
}) {
  return (
    <EmptyState
      icon={<Icon d={ICONS.mail} size={22} />}
      title="Connect your mailbox"
      body="Send from your own email. Replies show up here."
    >
      <div className="flex w-full max-w-[280px] flex-col gap-2">
        {providers.map((provider) => (
          <Button
            key={provider}
            variant="secondary"
            size="md"
            className="w-full"
            loading={connector.connectingProvider === provider}
            disabled={connector.connectingProvider !== null}
            onClick={() => connector.connect(provider)}
          >
            <MailboxProviderMark provider={provider} />
            Connect {providerLabel(provider)}
          </Button>
        ))}
      </div>
      <p className="mt-4 max-w-[340px] text-meta text-u-text3">
        Uncava only reads threads it starts, and only keeps meetings with people you&rsquo;ve mapped.
      </p>
    </EmptyState>
  );
}
