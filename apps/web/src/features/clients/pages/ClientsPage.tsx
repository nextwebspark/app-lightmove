import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, EmptyState, TableSkeleton } from "../../../components/ui";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { ListToolbar } from "../../../components/ui/ListToolbar";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridPaging } from "../../../lib/useGridPaging";
import { useGridSort, WORKSPACE_SCOPE } from "../../../lib/useGridSort";
import { NewProjectModal } from "../../projects/components/NewProjectModal";
import * as clientsApi from "../api/clientsApi";
import { ClientDrawer } from "../components/ClientDrawer";
import { ClientsList } from "../components/ClientsList";
import { NewClientModal } from "../components/NewClientModal";
import {
  CLIENT_COLUMN_VISIBILITY,
  CLIENT_SORT_FIELDS,
  clientColumns,
  type ClientSortField,
} from "../lib/clientColumns";
import { CHIPS, filterClients, type ChipKey } from "../lib/filtering";

const CLIENT_LAYOUT_COLUMNS = layoutColumnsOf(clientColumns);
const HIDEABLE_CLIENT_COLUMNS = hideableColumnsOf(clientColumns);

const DEFAULT_CLIENT_SORT = { field: "name", direction: "asc" } as const;

/**
 * The client registry: the list table, company-database-first create, and the record drawer. Records
 * are shared across projects — a client created here or inline from a mandate is the same row.
 */
export function ClientsPage() {
  const [query, setQuery] = useState("");
  const [chip, setChip] = useState<ChipKey>("all");
  const [openClientId, setOpenClientId] = useState<string | null>(null);
  const [newClientOpen, setNewClientOpen] = useState(false);
  const [newMandateOpen, setNewMandateOpen] = useState(false);
  const [sort, setSort] = useGridSort<ClientSortField>(
    "clients",
    WORKSPACE_SCOPE,
    CLIENT_SORT_FIELDS,
    DEFAULT_CLIENT_SORT,
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "clients",
    WORKSPACE_SCOPE,
    CLIENT_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout("clients", CLIENT_LAYOUT_COLUMNS);
  const paging = useGridPaging();

  const { data: clients = [], isPending, isError } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
  });

  const rows = useMemo(() => filterClients(clients, { chip, query }), [clients, chip, query]);
  const existingNames = useMemo(
    () => new Set(clients.map((client) => client.name.toLowerCase())),
    [clients],
  );

  // Narrowing the registry returns to the first page — page 3 of a two-row result is a blank grid.
  const { reset: resetPage } = paging;
  useEffect(() => resetPage(), [resetPage, chip, query, sort]);

  const newClientButton = (
    <Button onClick={() => setNewClientOpen(true)} className="!px-3.5 !py-[7px] !text-[13px]">
      <Icon d={ICONS.plus} size={15} />
      New client
    </Button>
  );

  // While the list is in flight, `clients` is still the [] default — without this gate the
  // "add your first client" empty state flashes before the table arrives.
  if (isPending) {
    return (
      <>
        <PageHeader title="Clients" subtitle="records shared across projects" action={newClientButton} />
        <TableSkeleton columns={["Client", "Type", "Client contact", "Sector", "Mandates", "Viewers"]} />
      </>
    );
  }

  // A refused read falls back to the [] default too, and "add your first client" over a 403 is a lie
  // twice: it states the firm has no clients, and it offers a button that cannot work. Say what
  // happened instead, and offer nothing.
  if (isError) {
    return (
      <>
        <PageHeader title="Clients" subtitle="records shared across projects" />
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load the client registry"
          body="You may no longer have access to it, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Clients"
        subtitle={`${clients.length} ${clients.length === 1 ? "client" : "clients"} · records shared across projects`}
        action={newClientButton}
      />

      {clients.length === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.clients} size={24} />}
          title="Add your first client"
          body="A client is the hiring entity a mandate is run for. Most already exist in the company database — search it first."
        >
          {newClientButton}
        </EmptyState>
      ) : (
        <>
          <ListToolbar
            query={query}
            onQuery={setQuery}
            placeholder="Search clients…"
            chips={CHIPS}
            chip={chip}
            onChip={setChip}
            trailing={
              <ColumnPicker
                columns={HIDEABLE_CLIENT_COLUMNS}
                visibility={columnVisibility}
                defaults={CLIENT_COLUMN_VISIBILITY}
                onChange={setColumnVisibility}
                onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
              />
            }
          />

          <div className="flex flex-col gap-3">
            <ClientsList
              clients={rows}
              sort={sort}
              onSortChange={setSort}
              columnVisibility={columnVisibility}
              onColumnVisibilityChange={setColumnVisibility}
              layout={layout}
              onLayoutChange={setLayout}
              pagination={paging.pagination}
              onPaginationChange={paging.onPaginationChange}
              onOpen={setOpenClientId}
            />
            <PaginationBar
              page={paging.page}
              size={paging.size}
              totalCount={rows.length}
              onPage={paging.setPage}
              onSize={paging.setSize}
              autoHide
            />
          </div>
        </>
      )}

      <ClientDrawer
        clientId={openClientId}
        onClose={() => setOpenClientId(null)}
        onNewMandate={() => setNewMandateOpen(true)}
      />

      {newClientOpen && (
        <NewClientModal
          open
          onClose={() => setNewClientOpen(false)}
          existingNames={existingNames}
          onCreated={(client) => setOpenClientId(client.id)}
        />
      )}

      {newMandateOpen && (
        <NewProjectModal
          open
          onClose={() => setNewMandateOpen(false)}
          clients={clients}
          lockedClientId={openClientId ?? undefined}
        />
      )}
    </>
  );
}
