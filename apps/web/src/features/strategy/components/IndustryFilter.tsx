import { useMemo, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { FacetCount, SectorGroup } from "../api/types";
import { TagCombobox } from "./TagCombobox";

const SPARKLE =
  "M9.9 2.6 11 5.9a2 2 0 0 0 1.3 1.3l3.3 1.1-3.3 1.1a2 2 0 0 0-1.3 1.3L9.9 14l-1.1-3.3a2 2 0 0 0-1.3-1.3L4.2 8.3l3.3-1.1a2 2 0 0 0 1.3-1.3ZM18 14l.6 1.8 1.8.6-1.8.6-.6 1.8-.6-1.8-1.8-.6 1.8-.6Z";

const ADJACENT_SHOWN = 6;
const LIST_ID = "industry-suggestions";

/**
 * The Industry panel: every industry in one box, plus the industries beside what is already picked.
 * A sector groups the labels and nothing more — never something the consultant selects, never stored.
 */
export function IndustryFilter({
  groups,
  adjacency,
  selected,
  onChange,
  children,
}: {
  groups: SectorGroup[];
  /** Which industries sit beside which, from the facets read. */
  adjacency: Record<string, string[]>;
  selected: string[];
  onChange: (industries: string[]) => void;
  /** The keyword half of the panel, which sits between the box and the suggestions it feeds. */
  children?: ReactNode;
}) {
  const [query, setQuery] = useState("");
  const [showAllAdjacent, setShowAllAdjacent] = useState(false);
  const chosen = new Set(selected);

  // A to Z, because this box is read by someone who already knows the industry they want: an
  // alphabetical list tells them where to look, where a size ranking makes them scan the whole of it.
  const leaves = useMemo(
    () =>
      groups
        .flatMap((group) => group.industries)
        .sort((a, b) => a.label.localeCompare(b.label)),
    [groups],
  );
  const leafOf = useMemo(() => {
    const byValue = new Map(leaves.map((leaf) => [leaf.value, leaf]));
    return (value: string) => byValue.get(value) ?? null;
  }, [leaves]);

  const needle = query.trim().toLowerCase();
  const offered = useMemo(
    () =>
      leaves.filter(
        (leaf) => !chosen.has(leaf.value) && (!needle || leaf.label.toLowerCase().includes(needle)),
      ),
    [leaves, selected, needle],
  );

  const addIndustry = (value: string) => onChange([...selected, value]);
  const removeIndustry = (value: string) =>
    onChange(selected.filter((entry) => entry !== value));

  // Appended in pick order, so taking a chip does not reshuffle the row.
  const adjacent = useMemo(() => {
    const suggestions: FacetCount[] = [];
    const listed = new Set(selected);
    for (const value of selected) {
      for (const neighbour of adjacency[value] ?? []) {
        const leaf = leafOf(neighbour);
        // A name the taxonomy no longer holds renders no chip rather than one selecting nothing.
        if (!leaf || listed.has(neighbour)) continue;
        listed.add(neighbour);
        suggestions.push(leaf);
      }
    }
    return suggestions;
  }, [selected, adjacency, leafOf]);

  return (
    <div className="flex flex-col gap-3">
      <TagCombobox
        listId={LIST_ID}
        noun="industries"
        tags={selected.map((value) => ({ value, label: leafOf(value)?.label ?? value }))}
        options={offered}
        emptyText="No industry matches that."
        onQueryChange={setQuery}
        onPick={addIndustry}
        onRemove={removeIndustry}
      />

      {children}

      {adjacent.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-line-soft pt-3">
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5">
              <Icon d={SPARKLE} size={13} className="flex-none text-amber" />
              <span className="font-sans text-[13px] font-semibold text-text">
                Adjacent Industries
              </span>
            </span>
            <span className="font-sans text-[11px] text-text3">Based on your selection</span>
          </div>

          <div role="group" aria-label="Adjacent Industries" className="flex flex-wrap gap-[6px]">
            {(showAllAdjacent ? adjacent : adjacent.slice(0, ADJACENT_SHOWN)).map((industry) => (
              <button
                key={industry.value}
                type="button"
                onClick={() => addIndustry(industry.value)}
                className="inline-flex items-center gap-1 rounded-full border border-line px-[9px] py-[5px] font-sans text-[12px] font-medium text-text2 transition hover:border-amber hover:text-amber"
              >
                <Icon d={ICONS.plus} size={10} className="flex-none" />
                {industry.label}
              </button>
            ))}
          </div>

          {adjacent.length > ADJACENT_SHOWN && (
            <button
              type="button"
              onClick={() => setShowAllAdjacent((shown) => !shown)}
              className="self-start font-sans text-[11px] text-text3 transition hover:text-text"
            >
              {showAllAdjacent ? "Show fewer" : `+ ${adjacent.length - ADJACENT_SHOWN} more industries`}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
