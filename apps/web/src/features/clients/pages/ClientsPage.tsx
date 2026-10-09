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
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as clientsApi from "../api/clientsApi";
import { ClientDrawer } from "../components/ClientDrawer";
import { ClientsList } from "../components/ClientsList";
import { NewClientModal } from "../components/NewClientModal";
import {
  CLIENT_COLUMN_VISIBILITY,
  CLIENT_SORT_FIELDS,
  clientColumnsFor,
  type ClientSortField,
} from "../lib/clientColumns";
import { chipsFor, CLIENT_CHIP_KEYS, filterClients, type ChipKey } from "../lib/filtering";
import { useAddressedSearch } from "../../../lib/useAddressedSearch";
import { useSearchParams } from "react-router-dom";


const DEFAULT_CLIENT_SORT = { field: "name", direction: "asc" } as const;

/**
 * The client registry: the list table, company-database-first create, and the record drawer. Records
 * are shared across projects — a client created here or inline from a mandate is the same row.
 */
export function ClientsPage() {
  const vocabulary = useWorkspaceVocabulary();
  const columns = useMemo(() => clientColumnsFor(vocabulary), [vocabulary]);
  const layoutColumns = useMemo(() => layoutColumnsOf(columns), [columns]);
  const hideableColumns = useMemo(() => hideableColumnsOf(columns), [columns]);
  const chips = useMemo(() => chipsFor(vocabulary), [vocabulary]);
  // In the address, with the open record, so the way back from a position opened here finds the page as left.
  const { query, setQuery, flushQuery, chip, setChip } = useAddressedSearch<ChipKey>("show", CLIENT_CHIP_KEYS, "all");
  const [searchParams, setSearchParams] = useSearchParams();
  const openClientId = searchParams.get("client");
  const setOpenClientId = (id: string | null) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (id) next.set("client", id);
        else next.delete("client");
        return next;
      },
      { replace: true },
    );
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
  const [layout, setLayout] = useGridLayout("clients", layoutColumns);
  const paging = useGridPaging();

  const { data: clients = [], isPending, isError } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
  });

  const rows = useMemo(() => filterClients(clients, { chip, query }), [clients, chip, query]);

  // Narrowing the registry returns to the first page — page 3 of a two-row result is a blank grid —
  // and a registry that shrank under the reader is clamped back onto its last page.
  const { reset: resetPage, clampTo } = paging;
  useEffect(() => {
    resetPage();
  }, [resetPage, chip, query, sort]);
  useEffect(() => {
    clampTo(rows.length);
  }, [clampTo, rows.length]);

  const newClientButton = (
    <Button onClick={() => setNewClientOpen(true)} size="sm">
      <Icon d={ICONS.plus} size={15} />
      New {vocabulary.unitLower}
    </Button>
  );

  // While the list is in flight, `clients` is still the [] default — without this gate the
  // "add your first client" empty state flashes before the table arrives.
  if (isPending) {
    return (
      <>
        <PageHeader
          title={vocabulary.units}
          subtitle={`${vocabulary.unitsLower} shared across reqs`}
          action={newClientButton}
        />
        <TableSkeleton columns={[vocabulary.unit, vocabulary.contacts, "Open positions", "Viewers"]} />
      </>
    );
  }

  // A refused read falls back to the [] default too, and "add your first client" over a 403 is a lie
  // twice: it states the firm has no clients, and it offers a button that cannot work. Say what
  // happened instead, and offer nothing.
  if (isError) {
    return (
      <>
        <PageHeader title={vocabulary.units} subtitle={`${vocabulary.unitsLower} shared across reqs`} />
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title={`Couldn't load the ${vocabulary.unitsLower}`}
          body="You may no longer have access to it, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={vocabulary.units}
        subtitle={`${clients.length} ${clients.length === 1 ? vocabulary.unitLower : vocabulary.unitsLower} · shared across reqs`}
        action={newClientButton}
      />

      {clients.length === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.clients} size={24} />}
          title={`Add your first ${vocabulary.unitLower}`}
          body={vocabulary.unitExplainer}
        >
          {newClientButton}
        </EmptyState>
      ) : (
        <>
          <ListToolbar
            query={query}
            onQueryChange={setQuery}
            onQueryBlur={flushQuery}
            placeholder={`Search ${vocabulary.unitsLower}…`}
            chips={chips}
            activeChip={chip}
            onChipChange={setChip}
            trailing={
              <ColumnPicker
                columns={hideableColumns}
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
          clients={clients}
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
