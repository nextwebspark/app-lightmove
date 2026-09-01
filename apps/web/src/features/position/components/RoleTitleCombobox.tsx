import { useComboboxList } from "../../strategy/lib/useComboboxList";
import type { PositionTemplate } from "../api/types";
import { SENIORITY_LABELS } from "../lib/labels";
import { CheckedInput } from "./fields";

const MAX_SUGGESTIONS = 8;

/**
 * The role title: a free-text field that suggests the templates the brief can be drafted from.
 *
 * Free text is the point. A mandate is titled "Group CFO – Energy Division" as often as it is titled
 * "Chief Financial Officer", and the title is what the whole workspace calls this search — so the
 * seventeen templates are offered, never imposed. Nothing is highlighted until somebody arrows into
 * the list or hovers it, which is what keeps Enter on a typed title from committing the suggestion
 * sitting underneath it.
 *
 * Picking one does two things, and they are separate on purpose: the title becomes the template's,
 * through the ordinary details write, and the brief is redrafted from that template.
 */
export function RoleTitleCombobox({
  value,
  templates,
  busy,
  onChange,
  onPick,
}: {
  value: string;
  templates: PositionTemplate[];
  busy: boolean;
  onChange: (roleTitle: string) => void;
  onPick: (template: PositionTemplate) => void;
}) {
  const matches = suggestionsFor(templates, value);

  const list = useComboboxList({
    optionCount: matches.length,
    autoHighlightFirst: false,
    onCommit: (index) => {
      const choice = matches[index];
      if (choice) onPick(choice);
    },
  });

  const showList = list.open && matches.length > 0;

  return (
    <div className="relative">
      <CheckedInput
        role="combobox"
        aria-expanded={showList}
        aria-controls="role-title-suggestions"
        aria-autocomplete="list"
        aria-activedescendant={
          showList && list.active >= 0 ? `role-title-suggestions-${list.active}` : undefined
        }
        aria-busy={busy}
        autoComplete="off"
        value={value}
        placeholder="e.g. Chief Financial Officer"
        onChange={(event) => {
          onChange(event.target.value);
          list.setActive(-1);
          list.setOpen(true);
        }}
        {...list.inputHandlers}
      />

      {showList && (
        <ul
          id="role-title-suggestions"
          role="listbox"
          aria-label="Role templates"
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-[10px] border border-line bg-panel py-1 shadow-panel"
        >
          {matches.map((template, index) => (
            <li
              key={template.id}
              id={`role-title-suggestions-${index}`}
              role="option"
              aria-selected={index === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, index)}
              onMouseEnter={() => list.setActive(index)}
              className={`flex cursor-pointer items-baseline gap-2.5 px-3 py-[7px] ${
                index === list.active ? "bg-panel2 text-text" : "text-text2"
              }`}
            >
              <span className="truncate font-sans text-[13px] font-medium text-text">
                {template.title}
              </span>
              <span className="min-w-0 flex-1 truncate text-right font-mono text-[10.5px] text-text3">
                {SENIORITY_LABELS[template.seniority]}
                {template.shared ? "" : " · yours"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * What the box offers: everything when it is empty — seventeen titles is a menu, not a search — and
 * the substring matches once somebody types, titles first so "chief" does not lead with a summary
 * that happens to mention it.
 */
function suggestionsFor(templates: PositionTemplate[], typed: string): PositionTemplate[] {
  const needle = typed.trim().toLowerCase();
  if (!needle) return templates.slice(0, MAX_SUGGESTIONS);

  const byTitle = templates.filter((template) => template.title.toLowerCase().includes(needle));
  const bySummary = templates.filter(
    (template) =>
      !byTitle.includes(template) && (template.summary ?? "").toLowerCase().includes(needle),
  );
  return [...byTitle, ...bySummary].slice(0, MAX_SUGGESTIONS);
}
