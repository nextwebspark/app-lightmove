import { cn } from "../../../lib/cn";
import { ClientMark } from "../../oauth/components/ClientMark";
import type { OAuthGrant } from "../api/types";
import { scopeChipClass } from "../lib/apiKeys";
import { grantHostOf, grantMetaOf } from "../lib/oauthGrants";

const PILL = "inline-flex rounded-full px-2 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em]";

export function OAuthGrantRow({
  grant,
  showOwner,
  canDisconnect,
  onDisconnect,
}: {
  grant: OAuthGrant;
  /** On the All view a connection names whose it is. */
  showOwner: boolean;
  canDisconnect: boolean;
  onDisconnect: () => void;
}) {
  return (
    <li className="py-3">
      <div className="flex items-center gap-3">
        <ClientMark client={grant} size="md" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13.5px] font-medium text-u-text">{grant.clientName}</span>
            <span
              className={cn(PILL, grant.verified ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal")}
            >
              {grant.verified ? "Verified" : "Unverified"}
            </span>
          </div>
          <div className="mt-0.5 font-mono text-[11.5px]/[1.55] text-u-text3 [overflow-wrap:anywhere]">
            <span className="text-u-text2">{grantHostOf(grant)}</span> · {grantMetaOf(grant, showOwner)}
          </div>
        </div>
        {canDisconnect && (
          <button
            type="button"
            onClick={onDisconnect}
            aria-label={`Disconnect ${grant.clientName}`}
            className="flex-none rounded-[5px] px-2 py-1 text-[12px] font-medium text-u-offlimits hover:bg-u-offlimits-tint"
          >
            Disconnect
          </button>
        )}
      </div>
      <div className="ms-12 mt-2 flex flex-wrap gap-1.5">
        {grant.scopes.map((scope) => (
          <code key={scope} className={cn("rounded-[4px] px-1.5 py-0.5 font-mono text-[11px]", scopeChipClass(scope))}>
            {scope}
          </code>
        ))}
      </div>
    </li>
  );
}
