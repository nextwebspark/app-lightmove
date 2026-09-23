import { cn } from "../../../lib/cn";
import { useComboboxList } from "../../../lib/useComboboxList";
import type { PositionTemplate } from "../api/types";
import { SENIORITY_LABELS } from "../lib/labels";
import { suggestionsFor } from "../lib/roleTitleSuggestions";
import { UnderlineField } from "./BriefFields";

const LIST_ID = "brief-role-title-suggestions";

/**
 * The role title on the brief: free text on a hairline that suggests the templates the brief can be
 * drafted from — the New-project modal's `RoleTitleCombobox`, in the brief's own skin.
 *
 * Free text is the point. A mandate is titled "Group CFO – Energy Division" as often as it is titled
 * "Chief Financial Officer", so the seventeen templates are offered, never imposed: nothing is
 * highlighted until somebody arrows into the list, which keeps Enter on a typed title from committing
 * the suggestion sitting underneath it. Picking one renames the search and redrafts the brief.
 */
export function RoleTitleField({
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
      <UnderlineField
        role="combobox"
        aria-label="Role title"
        aria-expanded={showList}
        aria-controls={LIST_ID}
        aria-autocomplete="list"
        aria-activedescendant={showList && list.active >= 0 ? `${LIST_ID}-${list.active}` : undefined}
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
          id={LIST_ID}
          role="listbox"
          aria-label="Role templates"
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-[10px] border border-u-border bg-u-bg py-1 shadow-u-e3"
        >
          {matches.map((template, index) => (
            <li
              key={template.id}
              id={`${LIST_ID}-${index}`}
              role="option"
              aria-selected={index === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, index)}
              onMouseEnter={() => list.setActive(index)}
              className={cn(
                "flex cursor-pointer items-baseline gap-2.5 px-3 py-[7px]",
                index === list.active ? "bg-u-accent-tint" : "",
              )}
            >
              <span className="truncate text-body font-medium text-u-text">{template.title}</span>
              <span className="min-w-0 flex-1 truncate text-right text-meta text-u-text3">
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
