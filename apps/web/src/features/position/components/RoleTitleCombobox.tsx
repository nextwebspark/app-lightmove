import { useComboboxList } from "../../../lib/useComboboxList";
import type { PositionTemplate } from "../api/types";
import { SENIORITY_LABELS } from "../lib/labels";
import { suggestionsFor } from "../lib/roleTitleSuggestions";
import { CheckedInput } from "./CheckedInput";

/**
 * The role title: free text that suggests the seventeen templates, never imposes one. Nothing is highlighted until
 * arrowed or hovered, so Enter keeps a typed title. Picking one sets the title and, separately, redrafts the brief.
 */
export function RoleTitleCombobox({
  value,
  templates,
  busy,
  invalid,
  onChange,
  onPick,
}: {
  value: string;
  templates: PositionTemplate[];
  busy: boolean;
  /** Marks the typed title as rejected — a red border, and no green check claiming otherwise. */
  invalid?: boolean;
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
        invalid={invalid}
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
          onMouseLeave={() => list.setActive(-1)}
          aria-label="Role templates"
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-[10px] border border-u-border-strong bg-u-surface py-1 shadow-u-e3"
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
                index === list.active ? "bg-u-raised text-u-text" : "text-u-text2"
              }`}
            >
              <span className="truncate font-sans text-body font-medium text-u-text">
                {template.title}
              </span>
              <span className="min-w-0 flex-1 truncate text-right font-mono text-meta text-u-text3">
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
