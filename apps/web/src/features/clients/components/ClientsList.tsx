import type { ColumnVisibilityState, OnChangeFn, PaginationState } from "@tanstack/react-table";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Client } from "../api/types";
import {
  CLIENT_COLUMN_PINNING,
  clientColumns,
  clientTableFeatures,
  locationOf,
  RepStack,
  TypePill,
  ViewerCell,
  type ClientSortField,
} from "../lib/clientColumns";

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
      label="Clients"
      fit="content"
      layout={layout}
      onLayoutChange={onLayoutChange}
      // The page answers the pending and refused reads before it renders this.
      loading={false}
      error={false}
      errorMessage="That list could not be loaded. Refresh, or check you still have access."
      emptyMessage="No clients match. Clear the search or add a new client."
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
      className="flex w-full flex-col gap-2.5 rounded-[10px] border border-line bg-panel p-3.5 text-left transition hover:bg-panel2"
    >
      <div className="flex items-start gap-2.5">
        <CompanyLogo name={client.name} logo={client.logoUrl} size={28} />
        <span className="min-w-0 flex-1 text-[13.5px] font-semibold text-text">{client.name}</span>
        <span className="flex-none">
          <TypePill type={client.type} />
        </span>
      </div>

      <div className="font-mono text-[11.5px] text-text3">
        {[locationOf(client), client.sector].filter(Boolean).join(" · ") || "—"}
      </div>

      <div className="flex items-center gap-2.5 border-t border-line-soft pt-2.5">
        <RepStack contacts={client.contacts} />
        <span className="ml-auto font-mono text-[11px] text-text2">
          <b className="font-semibold text-text">{client.activeMandates}</b> active
        </span>
        <ViewerCell viewers={client.viewers} />
      </div>
    </button>
  );
}
