import { cn } from "../../../lib/cn";
import type { ApiKeyScope } from "../api/types";
import { scopeChipClass } from "../lib/apiKeys";

/** The small uppercase pill an API key row and an AI app row put their kind and status in. */
export const STATUS_PILL =
  "inline-flex rounded-full px-2 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em]";

/** What a key or a connection may read, one chip a scope, personal data and `mcp:use` in their own tones. */
export function ScopeChips({ scopes, className }: { scopes: readonly ApiKeyScope[]; className?: string }) {
  return (
    <div className={cn("flex flex-wrap gap-1.5", className)}>
      {scopes.map((scope) => (
        <code key={scope} className={cn("rounded-[4px] px-1.5 py-0.5 font-mono text-[11px]", scopeChipClass(scope))}>
          {scope}
        </code>
      ))}
    </div>
  );
}
