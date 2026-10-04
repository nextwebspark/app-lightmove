import type { ColumnVisibilityState, OnChangeFn, RowSelectionState } from "@tanstack/react-table";
import { DataGrid } from "../../../../components/ui/DataGrid";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import { useDataGridTable } from "../../../../lib/useDataGridTable";
import { layoutColumnsOf, useGridLayout } from "../../../../lib/useGridLayout";
import type { PersonResult } from "../../api/types";
import { PersonCard } from "./PersonCard";
import { PERSON_COLUMN_PINNING, personColumns, personTableFeatures } from "../../lib/personColumns";

const PERSON_LAYOUT_COLUMNS = layoutColumnsOf(personColumns);

/** ContactOut's order is the only order: the grid takes a sort it never changes. */
const UNSORTED = { field: "name", direction: "asc" } as const;
const ALL_COLUMNS: ColumnVisibilityState = {};

/**
 * The people a search returned over the shared {@link DataGrid}, so they read like the Companies
 * screens: pinned name, logos, LinkedIn marks, ticks in the leading slot. A person already in the
 * mandate has no tick — they came back, and were billed, because ContactOut cannot leave one out.
 */
export function PersonResultsTable({
  people,
  loading,
  rowSelection,
  onRowSelectionChange,
  onOpen,
}: {
  people: PersonResult[];
  loading: boolean;
  rowSelection: RowSelectionState;
  onRowSelectionChange: OnChangeFn<RowSelectionState>;
  /** Reads the whole profile, from the page already fetched. */
  onOpen: (person: PersonResult) => void;
}) {
  const [layout, setLayout] = useGridLayout("strategyPeople", PERSON_LAYOUT_COLUMNS);
  const table = useDataGridTable<typeof personTableFeatures, PersonResult, string>({
    features: personTableFeatures,
    columns: personColumns,
    data: people,
    getRowId: (person) => person.linkedinSlug,
    pinning: PERSON_COLUMN_PINNING,
    sort: UNSORTED,
    onSortChange: () => undefined,
    columnVisibility: ALL_COLUMNS,
    onColumnVisibilityChange: () => undefined,
    layout,
    onLayoutChange: setLayout,
    rowSelection,
    onRowSelectionChange,
  });

  const tickable = people.filter((person) => !person.held);
  const tickedCount = tickable.filter((person) => rowSelection[person.linkedinSlug]).length;

  return (
    <DataGrid
      table={table}
      label="People"
      layout={layout}
      onLayoutChange={setLayout}
      loading={loading}
      error={false}
      errorMessage="Those people could not be loaded."
      emptyMessage="Nobody matched."
      onRowClick={onOpen}
      renderCard={(person) => (
        <PersonCard
          person={person}
          selected={Boolean(rowSelection[person.linkedinSlug])}
          onToggle={person.held ? undefined : () => table.getRow(person.linkedinSlug).toggleSelected()}
          onOpen={onOpen}
        />
      )}
      headerLead={
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
      }
      rowLead={(person) =>
        person.held ? (
          <span aria-hidden className="size-4 flex-none" />
        ) : (
          <SelectionCheckbox
            checked={Boolean(rowSelection[person.linkedinSlug])}
            label={`Select ${person.fullName ?? person.linkedinSlug}`}
            onChange={table.getRow(person.linkedinSlug).getToggleSelectedHandler()}
          />
        )
      }
    />
  );
}
