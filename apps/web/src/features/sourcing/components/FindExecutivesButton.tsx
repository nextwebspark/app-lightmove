import { Icon, ICONS } from "../../../components/layout/Icon";
import { TOOLBAR_PRIMARY } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { FindButtonState } from "../lib/useSourcingRun";

/**
 * The toolbar's Find executives, which carries the search: idle, finding with its progress (still a button — it
 * opens the panel on the live progress), a count of people found and not yet reviewed, or a dot after a search
 * stopped short.
 */
export function FindExecutivesButton({ state, onPress }: { state: FindButtonState; onPress: () => void }) {
  const title =
    state.kind === "running"
      ? "Open the search in progress"
      : state.kind === "ready"
        ? `${state.count} found — review them`
        : state.kind === "failed"
          ? "The last search stopped before it finished"
          : "Find executives at the ticked companies, or at the first ones with nobody mapped";
  return (
    <button type="button" title={title} onClick={onPress} className={cn(TOOLBAR_PRIMARY, "relative")}>
      {state.kind === "running" ? (
        <span
          aria-hidden="true"
          className="size-[13px] flex-none animate-spin rounded-full border-2 border-white/35 border-t-white"
        />
      ) : (
        <Icon d={ICONS.search} size={15} className="flex-none" />
      )}
      {state.kind === "running" ? `Finding… ${state.progress}`.trimEnd() : "Find executives"}
      {state.kind === "ready" && (
        <span className="rounded-full bg-white px-[7px] py-px text-meta font-bold tabular-nums text-u-accent-solid">
          {state.count}
        </span>
      )}
      {state.kind === "failed" && (
        <span
          aria-label="Stopped before it finished"
          className="absolute -right-1 -top-1 size-2.5 rounded-full border-2 border-u-surface bg-u-signal"
        />
      )}
    </button>
  );
}
