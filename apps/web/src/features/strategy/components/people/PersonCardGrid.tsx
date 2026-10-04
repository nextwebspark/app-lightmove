import type { OnChangeFn, RowSelectionState } from "@tanstack/react-table";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import type { PersonResult } from "../../api/types";
import { PersonCard, PersonCardSkeleton } from "./PersonCard";

const SKELETON_CARDS = 8;

/**
 * The Cards view of a search: the same people, ticks and drawer as {@link PersonResultsTable}, so
 * switching views changes how a page reads and nothing about what is selected or filed.
 */
export function PersonCardGrid({
  people,
  loading,
  total,
  rowSelection,
  onRowSelectionChange,
  onOpen,
}: {
  people: PersonResult[];
  loading: boolean;
  /** How many the search matched in all, for the strip above the cards. */
  total: number;
  rowSelection: RowSelectionState;
  onRowSelectionChange: OnChangeFn<RowSelectionState>;
  onOpen: (person: PersonResult) => void;
}) {
  const tickable = people.filter((person) => !person.held);
  const tickedCount = tickable.filter((person) => rowSelection[person.linkedinSlug]).length;

  const toggle = (slug: string) =>
    onRowSelectionChange((current) => {
      const next = { ...current };
      if (next[slug]) delete next[slug];
      else next[slug] = true;
      return next;
    });

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-y-auto" aria-busy={loading}>
      {people.length > 0 && (
        <div className="flex items-center gap-2.5 px-1 pb-2 text-meta text-u-text3">
          <SelectionCheckbox
            checked={tickable.length > 0 && tickedCount === tickable.length}
            indeterminate={tickedCount > 0 && tickedCount < tickable.length}
            label="Select everyone on this list"
            onChange={() =>
              onRowSelectionChange(
                tickedCount === tickable.length
                  ? {}
                  : Object.fromEntries(tickable.map((person) => [person.linkedinSlug, true])),
              )
            }
          />
          <span>
            {people.length.toLocaleString()} of {total.toLocaleString()} people
          </span>
        </div>
      )}
      <ul aria-label="People" className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-3 px-1 pb-3">
        {people.map((person) => (
          <li key={person.linkedinSlug} className="flex">
            <PersonCard
              person={person}
              selected={Boolean(rowSelection[person.linkedinSlug])}
              onToggle={person.held ? undefined : () => toggle(person.linkedinSlug)}
              onOpen={onOpen}
            />
          </li>
        ))}
        {loading &&
          Array.from({ length: people.length === 0 ? SKELETON_CARDS : 4 }, (_, index) => (
            <li key={`skeleton-${index}`} aria-hidden>
              <PersonCardSkeleton />
            </li>
          ))}
      </ul>
    </div>
  );
}
