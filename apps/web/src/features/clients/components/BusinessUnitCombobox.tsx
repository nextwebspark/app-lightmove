import { Input } from "../../../components/ui";
import { useComboboxList } from "../../../lib/useComboboxList";
import type { Client } from "../api/types";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import { ComboboxOption, NewNameOption } from "./ComboboxRows";

const LIST_ID = "business-unit-options";

/**
 * One field for the business unit: type to find an existing one, or keep typing to name a new one.
 * Enter keeps what was typed rather than committing the unit underneath it, because the typed name
 * is itself a valid answer.
 */
export function BusinessUnitCombobox({
  value,
  clients,
  invalid,
  onChange,
}: {
  value: string;
  clients: Client[];
  invalid?: boolean;
  onChange: (name: string) => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const query = value.trim().toLowerCase();
  const matches = query ? clients.filter((client) => client.name.toLowerCase().includes(query)) : clients;
  const offerNew = query !== "" && !clients.some((client) => client.name.toLowerCase() === query);
  const optionCount = matches.length + (offerNew ? 1 : 0);

  const list = useComboboxList({
    optionCount,
    autoHighlightFirst: false,
    onCommit: (index) => {
      const choice = matches[index];
      onChange(choice ? choice.name : value.trim());
    },
  });

  const showList = list.open && optionCount > 0;

  return (
    <div className="relative">
      <Input
        role="combobox"
        invalid={invalid}
        aria-expanded={showList}
        aria-controls={LIST_ID}
        aria-autocomplete="list"
        aria-activedescendant={showList && list.active >= 0 ? `${LIST_ID}-${list.active}` : undefined}
        autoComplete="off"
        value={value}
        placeholder={`Search or name a new ${vocabulary.unitLower}`}
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
          aria-label={vocabulary.units}
          className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-[10px] border border-u-border-strong bg-u-surface py-1 shadow-u-e3"
        >
          {matches.map((client, index) => (
            <ComboboxOption key={client.id} listId={LIST_ID} index={index} list={list}>
              <span className="truncate font-medium text-u-text">{client.name}</span>
            </ComboboxOption>
          ))}
          {offerNew && (
            <NewNameOption listId={LIST_ID} index={matches.length} list={list}>
              Create <span className="font-medium text-u-text">“{value.trim()}”</span>
            </NewNameOption>
          )}
        </ul>
      )}
    </div>
  );
}
