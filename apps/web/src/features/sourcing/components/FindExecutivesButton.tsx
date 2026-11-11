import { Icon, ICONS } from "../../../components/layout/Icon";
import { TOOLBAR_PRIMARY } from "../../../components/ui";
import { ToolbarLabel } from "../../../components/ui/Toolbar";
import { Popover } from "../../../components/ui/Popover";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import type { FindButtonState } from "../lib/useSourcingRun";

/** The position's latest search, as the button's menu names it. */
export interface LastSearch {
  summary: string;
  onOpen: () => void;
}

/**
 * The toolbar's Find executives, which carries the search: idle, finding with its progress (still a button — it
 * opens the panel on the live progress), a count of people found and not yet reviewed, or a dot after a search
 * stopped short. Idle, it searches the ticked companies, and says why not when none or too many are ticked. Its
 * caret opens the last search whatever is ticked, so results nobody has added yet are always one press away.
 */
export function FindExecutivesButton({
  state,
  onPress,
  unavailable,
  companies = 0,
  last,
}: {
  state: FindButtonState;
  onPress: () => void;
  /** Why the button cannot search now; pressing it says so. */
  unavailable?: string;
  /** Companies ticked, each once. */
  companies?: number;
  last?: LastSearch;
}) {
  const toast = useToast();
  const title =
    unavailable ??
    (state.kind === "running"
      ? "Open the search in progress"
      : state.kind === "ready"
        ? `${state.count} found — review them`
        : state.kind === "failed"
          ? "The last search stopped before it finished"
          : "Find executives at the ticked companies");
  return (
    <span className="relative inline-flex">
      <button
        type="button"
        title={title}
        aria-disabled={unavailable ? true : undefined}
        onClick={() => (unavailable ? toast(unavailable) : onPress())}
        className={cn(TOOLBAR_PRIMARY, last && "rounded-r-none")}
      >
        {state.kind === "running" ? (
          <span
            aria-hidden="true"
            className="size-[13px] flex-none animate-spin rounded-full border-2 border-white/35 border-t-white"
          />
        ) : (
          <Icon d={ICONS.search} size={15} className="flex-none" />
        )}
        <ToolbarLabel>{state.kind === "running" ? `Finding… ${state.progress}`.trimEnd() : "Find executives"}</ToolbarLabel>
        {state.kind === "ready" && (
          <span className="rounded-full bg-white px-[7px] py-px text-meta font-bold tabular-nums text-u-accent-solid">
            {state.count}
          </span>
        )}
        {state.kind !== "ready" && state.kind !== "running" && companies > 0 && (
          <span className="tabular-nums opacity-80">· {companies}</span>
        )}
      </button>
      {last && (
        <Popover
          label="Find executives options"
          align="right"
          width={280}
          hasPopup="menu"
          triggerClassName={cn(TOOLBAR_PRIMARY, "rounded-l-none border-l border-white/25 px-2")}
          trigger={() => <Icon d={ICONS.chevronDown} size={13} />}
        >
          {(close) => (
            <div role="menu" aria-label="Find executives options">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  close();
                  last.onOpen();
                }}
                className="flex w-full flex-col items-start gap-0.5 rounded px-2 py-1.5 text-left hover:bg-u-raised"
              >
                <span className="text-body font-medium text-u-text">Open the last search</span>
                <span className="text-note text-u-text3">{last.summary}</span>
              </button>
            </div>
          )}
        </Popover>
      )}
      {state.kind === "failed" && (
        <span
          aria-label="Stopped before it finished"
          className="absolute -right-1 -top-1 size-2.5 rounded-full border-2 border-u-surface bg-u-signal"
        />
      )}
    </span>
  );
}
