import { useEffect, useMemo, useState } from "react";
import type { SectorGroup } from "../api/types";
import { Icon } from "../../../components/layout/Icon";
import { CheckBox } from "./FilterCheckRow";

const CHEVRON_OPEN = "m6 9 6 6 6-6";
const CHEVRON_CLOSED = "m9 18 6-6-6-6";
const SEARCH = "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm10 18-4.3-4.3";
const CLOSE = "M18 6 6 18M6 6l12 12";

/**
 * The Industry panel: the selection as removable tags, a search box, and a two-level checkbox tree.
 *
 * <p>This is the one axis that cannot be a flat list. The universe carries 148 industry labels, so
 * the API groups them into twenty sectors and each renders as a parent row with its leaves behind a
 * chevron. Ticking the parent ticks every leaf; it draws indeterminate when only some are on.
 *
 * <p><b>Selecting a group stores its industries, never the group name.</b> The grouping is editorial
 * and will be re-tuned; a saved search that silently widened because someone moved a label between
 * sectors would be a scope change nobody asked for.
 *
 * <p><b>Apply is deliberate, and Industry alone has it.</b> Every other panel writes through on
 * click, which is right when one click is one decision. Here a single intent — "technology, but not
 * computer games" — is six or seven clicks, and autosaving each one would re-run the query, re-page
 * the table and write an audit event for every intermediate state the consultant never meant to
 * express. So the tree edits a draft and the amber button commits it.
 */
export function IndustryFilter({
  groups,
  selected,
  onApply,
}: {
  groups: SectorGroup[];
  selected: string[];
  onApply: (industries: string[]) => void;
}) {
  const [draft, setDraft] = useState<string[]>(selected);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [query, setQuery] = useState("");

  // A saved search loaded from the toolbar replaces the committed selection under the open panel;
  // the draft has to follow it, or Apply would silently re-commit the filter that was just replaced.
  useEffect(() => setDraft(selected), [selected]);

  const draftSet = useMemo(() => new Set(draft), [draft]);
  const isDirty =
    draft.length !== selected.length || draft.some((value) => !selected.includes(value));

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return groups;
    return groups
      .map((group) => ({
        ...group,
        industries: group.name.toLowerCase().includes(needle)
          ? group.industries
          : group.industries.filter((leaf) => leaf.label.toLowerCase().includes(needle)),
      }))
      .filter((group) => group.industries.length > 0);
  }, [groups, query]);

  const toggleLeaf = (value: string) => {
    setDraft((current) =>
      current.includes(value) ? current.filter((entry) => entry !== value) : [...current, value],
    );
  };

  const toggleGroup = (group: SectorGroup) => {
    const leaves = group.industries.map((leaf) => leaf.value);
    const allOn = leaves.every((leaf) => draftSet.has(leaf));
    setDraft((current) =>
      allOn
        ? current.filter((entry) => !leaves.includes(entry))
        : [...new Set([...current, ...leaves])],
    );
  };

  return (
    <div className="flex flex-col gap-3">
      {draft.length > 0 && (
        <div className="flex flex-wrap gap-[5px]">
          {draft.map((value) => (
            <button
              key={value}
              type="button"
              title="Remove"
              onClick={() => toggleLeaf(value)}
              className="inline-flex items-center gap-1 rounded-[4px] bg-amber-dim px-[7px] py-1 shadow-[inset_0_0_0_1px_var(--color-amber-dim)]"
            >
              <span className="font-sans text-[11px] font-medium text-amber">{value}</span>
              <Icon d={CLOSE} size={8} className="text-text3" />
            </button>
          ))}
        </div>
      )}

      <div className="h-px bg-line-soft" />

      <label className="flex h-8 items-center gap-2 rounded-md border border-line bg-panel2 px-[10px]">
        <Icon d={SEARCH} size={13} className="flex-none text-text3" />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search industries..."
          aria-label="Search industries"
          className="w-full bg-transparent font-sans text-[12px] font-medium text-text outline-none"
        />
      </label>

      <div className="flex flex-col">
        {visible.map((group) => {
          const leaves = group.industries.map((leaf) => leaf.value);
          const chosen = leaves.filter((leaf) => draftSet.has(leaf)).length;
          const isOpen = expanded.includes(group.name) || query.trim() !== "";

          return (
            <div key={group.name}>
              <div className="flex items-center justify-between py-[7px] pr-[2px]">
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={chosen === leaves.length ? true : chosen > 0 ? "mixed" : false}
                  onClick={() => toggleGroup(group)}
                  className="flex items-center gap-2"
                >
                  <CheckBox
                    checked={chosen === leaves.length && leaves.length > 0}
                    indeterminate={chosen > 0}
                    size="sm"
                  />
                  <span className="font-sans text-[13px] font-semibold text-text">{group.name}</span>
                </button>
                <button
                  type="button"
                  aria-label={`${isOpen ? "Collapse" : "Expand"} ${group.name}`}
                  aria-expanded={isOpen}
                  onClick={() =>
                    setExpanded((current) =>
                      current.includes(group.name)
                        ? current.filter((entry) => entry !== group.name)
                        : [...current, group.name],
                    )
                  }
                  className="px-1 py-[2px] text-text3 transition hover:text-text"
                >
                  <Icon d={isOpen ? CHEVRON_OPEN : CHEVRON_CLOSED} size={12} />
                </button>
              </div>

              {isOpen && (
                <div className="flex flex-col pb-1 pl-[25px]">
                  {group.industries.map((leaf) => (
                    <button
                      key={leaf.value}
                      type="button"
                      role="checkbox"
                      aria-checked={draftSet.has(leaf.value)}
                      onClick={() => toggleLeaf(leaf.value)}
                      className="flex items-center gap-[7px] px-[2px] py-[5px] text-left"
                    >
                      <CheckBox checked={draftSet.has(leaf.value)} size="sm" />
                      <span className="font-sans text-[12px] font-medium text-text">
                        {leaf.label}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="h-px bg-line-soft" />

      <button
        type="button"
        onClick={() => onApply(draft)}
        disabled={!isDirty}
        className="w-full rounded-lg bg-amber-btn py-[10px] font-sans text-[13px] font-semibold text-on-amber transition hover:brightness-105 disabled:opacity-40"
      >
        Apply Industry Filter
      </button>
    </div>
  );
}
