import { cn } from "../../../lib/cn";
import type { ApiKey } from "../api/types";
import { KIND_LABEL, metaLineOf, scopeChipClass, statusOf, usageLineOf, type ApiKeyTone } from "../lib/apiKeys";

const PILL = "inline-flex rounded-full px-2 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em]";

const TONE: Record<ApiKeyTone, string> = {
  active: "bg-u-direct-tint text-u-direct",
  soon: "bg-u-signal-tint text-u-signal",
  expired: "bg-u-offlimits-tint text-u-offlimits",
  revoked: "bg-u-surface text-u-text3",
};

export function ApiKeyRow({
  apiKey,
  showOwner,
  canRevoke,
  onRevoke,
}: {
  apiKey: ApiKey;
  /** On the All view a personal key names whose it is. */
  showOwner: boolean;
  canRevoke: boolean;
  onRevoke: () => void;
}) {
  const status = statusOf(apiKey);
  const dead = status.tone === "expired" || status.tone === "revoked";

  return (
    <li className={cn("py-3", dead && "opacity-60")}>
      <div className="flex flex-wrap items-center gap-2">
        <span className="min-w-0 truncate text-[13.5px] font-medium text-u-text">{apiKey.name}</span>
        <span
          className={cn(
            PILL,
            apiKey.kind === "SERVICE"
              ? "bg-u-adjacent-tint text-u-adjacent"
              : "border border-u-border bg-u-surface text-u-text2",
          )}
        >
          {KIND_LABEL[apiKey.kind]}
        </span>
        <span className={cn(PILL, TONE[status.tone])}>{status.label}</span>
        {canRevoke && !dead && (
          <button
            type="button"
            onClick={onRevoke}
            aria-label={`Revoke ${apiKey.name}`}
            className="ms-auto rounded-[5px] px-2 py-1 text-[12px] font-medium text-u-offlimits hover:bg-u-offlimits-tint"
          >
            Revoke
          </button>
        )}
      </div>
      <div className="mt-1 font-mono text-[11.5px]/[1.55] text-u-text3 [overflow-wrap:anywhere]">
        <span className="text-u-text2">{apiKey.tokenHint}</span> · {metaLineOf(apiKey, showOwner)}
      </div>
      <div className="mt-1 font-mono text-[11.5px]/[1.55] text-u-text3">{usageLineOf(apiKey)}</div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {apiKey.scopes.map((scope) => (
          <code
            key={scope}
            className={cn("rounded-[4px] px-1.5 py-0.5 font-mono text-[11px]", scopeChipClass(scope))}
          >
            {scope}
          </code>
        ))}
      </div>
    </li>
  );
}
