import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { AssistantRefinement, AssistantRefinements as Refinements } from "../api/types";

const WIDENING: ReadonlySet<AssistantRefinement["kind"]> = new Set(["ADD_INDUSTRY", "ADD_COUNTRY", "ANY_SIZE", "DROP_KEYWORD"]);

function tagOf(kind: AssistantRefinement["kind"]): string {
  if (kind === "ADD_INDUSTRY") return "Adjacent · transferable talent";
  return WIDENING.has(kind) ? "Widen" : "Narrow";
}

function tallyOf(refinements: Refinements): string {
  const band = `${refinements.targetMin}–${refinements.targetMax}`;
  const total = `${refinements.inMandate} in the mandate + ${refinements.newFromSearch} new → ${refinements.projected}`;
  const inRange = refinements.projected >= refinements.targetMin && refinements.projected <= refinements.targetMax;
  return inRange ? `${total} · in range` : `${total} · aim ${band}`;
}

/**
 * The next searches offered under the latest answer, each counted against the mandate's universe so the
 * consultant can steer it toward the target without typing. Pressing one asks it.
 */
export function AssistantRefinements({
  refinements,
  disabled,
  onPick,
}: {
  refinements: Refinements;
  disabled: boolean;
  onPick: (prompt: string) => void;
}) {
  return (
    <div className="mt-3">
      <p className="font-mono text-[10.5px] text-u-text3">{tallyOf(refinements)}</p>
      {refinements.options.length > 0 && (
        <ul className="mt-1.5 space-y-1.5" aria-label="Refine the search">
          {refinements.options.map((option) => (
            <li key={option.prompt}>
              <RefinementButton option={option} disabled={disabled} onPick={onPick} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RefinementButton({
  option,
  disabled,
  onPick,
}: {
  option: AssistantRefinement;
  disabled: boolean;
  onPick: (prompt: string) => void;
}) {
  const adjacent = option.kind === "ADD_INDUSTRY";
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onPick(option.prompt)}
      title={option.prompt}
      className={cn(
        "group flex w-full items-center gap-2.5 rounded-lg border border-u-border-strong px-2.5 py-1.5 text-start transition hover:border-u-inferred hover:bg-u-inferred-tint disabled:opacity-40",
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
          {tagOf(option.kind)}
        </span>
        <span className="block font-sans text-xs leading-[1.45] text-u-text2 group-hover:text-u-text">
          {option.label}
        </span>
      </span>
      <span className="flex-none font-mono text-[11px] text-u-text3">→ {option.projected}</span>
      <Icon
        d={ICONS.arrowUp}
        size={13}
        className="flex-none text-u-inferred opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100"
      />
    </button>
  );
}
