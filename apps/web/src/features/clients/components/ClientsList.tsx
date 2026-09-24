import type { ColumnVisibilityState, OnChangeFn, PaginationState } from "@tanstack/react-table";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Client } from "../api/types";
import {
  CLIENT_COLUMN_PINNING,
  clientColumns,
  clientTableFeatures,
  RepStack,
  ViewerCell,
  type ClientSortField,
} from "../lib/clientColumns";
import { BusinessUnitGlyph } from "./BusinessUnitGlyph";
import { openPositionsLabel } from "../lib/openPositions";

/** The client registry: the shared grid on a wide screen, a stack of cards below `md`. */
export function ClientsList({
  clients,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
  pagination,
  onPaginationChange,
  onOpen,
}: {
  clients: Client[];
  sort: GridSort<ClientSortField>;
  onSortChange: (sort: GridSort<ClientSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  pagination: PaginationState;
  onPaginationChange: OnChangeFn<PaginationState>;
  onOpen: (clientId: string) => void;
}) {
  const table = useDataGridTable<typeof clientTableFeatures, Client, ClientSortField>({
    features: clientTableFeatures,
    columns: clientColumns,
    data: clients,
    getRowId: (client) => client.id,
    pinning: CLIENT_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    pagination,
    onPaginationChange,
  });

  return (
    <DataGrid
      table={table}
      label="Business units"
      fit="content"
      layout={layout}
      onLayoutChange={onLayoutChange}
      // The page answers the pending and refused reads before it renders this.
      loading={false}
      error={false}
      errorMessage="That list could not be loaded. Refresh, or check you still have access."
      emptyMessage="No business units match. Clear the search or add a new one."
      onRowClick={(client) => onOpen(client.id)}
      renderCard={(client) => <ClientCard client={client} onOpen={() => onOpen(client.id)} />}
    />
  );
}

function ClientCard({ client, onOpen }: { client: Client; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex w-full flex-col gap-2.5 rounded-[10px] border border-u-border-strong bg-u-surface p-3.5 text-left transition hover:bg-u-raised"
    >
      <div className="flex items-start gap-2.5">
        <BusinessUnitGlyph size={26} />
        <span className="min-w-0 flex-1">
          <span className="block text-[13.5px] font-semibold text-u-text">{client.name}</span>
          <span className="block font-mono text-[11.5px] text-u-text3">
            {openPositionsLabel(client.activeMandates)}
          </span>
        </span>
      </div>

      <div className="flex items-center justify-between gap-2.5 border-t border-u-border pt-2.5">
        <RepStack contacts={client.contacts} />
        <ViewerCell viewers={client.viewers} />
      </div>
    </button>
  );
}
