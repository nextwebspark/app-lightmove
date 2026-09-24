import { useQuery } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import * as assistantApi from "../api/assistantApi";
import type { AssistantStarter } from "../api/types";

const KIND_TAGS: Record<AssistantStarter["kind"], string> = {
  SECTOR: "Your sector",
  ADJACENT: "Adjacent · transferable talent",
  SIZE: "Similar size",
};

const FALLBACK: AssistantStarter[] = [{ kind: "SECTOR", prompt: "Top 10 Retail companies" }];

/**
 * The empty chat's suggestions, drawn from the firm the workspace is. Pressing one asks it straight
 * away; the adjacent sectors are marked out because they are where transferable talent sits.
 */
export function AssistantStarters({
  projectId,
  disabled,
  onPick,
}: {
  projectId: string;
  disabled: boolean;
  onPick: (prompt: string) => void;
}) {
  const suggestions = useQuery({
    queryKey: assistantApi.ASSISTANT_STARTERS_KEY(projectId),
    queryFn: () => assistantApi.listStarters(projectId),
    staleTime: 10 * 60_000,
  });

  const starters = suggestions.isError ? FALLBACK : (suggestions.data?.starters ?? []);

  return (
    <div className="my-auto">
      <div className="mb-4 text-center">
        <p className="font-sans text-[13px] text-u-text2">Find companies for this mandate.</p>
        <p className="mt-1 font-mono text-[11px] text-u-text3">Suggested from your firm's sector</p>
      </div>

      {suggestions.isLoading ? (
        <div aria-hidden className="space-y-1.5">
          {[0, 1, 2].map((row) => (
            <div key={row} className="h-[54px] animate-pulse rounded-lg bg-u-raised" />
          ))}
        </div>
      ) : (
        <ul className="space-y-1.5">
          {starters.map((starter) => (
            <li key={starter.prompt}>
              <StarterButton starter={starter} disabled={disabled} onPick={onPick} />
            </li>
          ))}
        </ul>
      )}

      {suggestions.data?.sectorAssumed && (
        <p className="mt-3 text-center font-mono text-[10.5px] text-u-text3">
          Assuming retail — add your company in Settings → General to tailor these.
        </p>
      )}
    </div>
  );
}

function StarterButton({
  starter,
  disabled,
  onPick,
}: {
  starter: AssistantStarter;
  disabled: boolean;
  onPick: (prompt: string) => void;
}) {
  const adjacent = starter.kind === "ADJACENT";
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onPick(starter.prompt)}
      className={cn(
        "group flex w-full items-center gap-2.5 rounded-lg border border-u-border-strong px-2.5 py-2 text-start transition hover:border-u-inferred hover:bg-u-inferred-tint disabled:opacity-40",
        adjacent && "border-s-2 border-s-u-inferred",
      )}
    >
      <span className="min-w-0 flex-1">
        <span
          className={cn(
            "mb-0.5 inline-block rounded px-1 font-mono text-[9.5px] uppercase tracking-[0.04em]",
            adjacent ? "bg-u-inferred-tint text-u-inferred" : "text-u-text3",
          )}
        >
          {KIND_TAGS[starter.kind]}
        </span>
        <span className="block font-sans text-xs leading-[1.45] text-u-text2 group-hover:text-u-text">
          {starter.prompt}
        </span>
      </span>
      <Icon
        d={ICONS.arrowUp}
        size={13}
        className="flex-none text-u-inferred opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100"
      />
    </button>
  );
}
