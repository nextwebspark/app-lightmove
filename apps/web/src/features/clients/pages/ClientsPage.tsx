import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, EmptyState } from "../../../components/ui";
import { NewProjectModal } from "../../projects/components/NewProjectModal";
import * as clientsApi from "../api/clientsApi";
import { ClientDrawer } from "../components/ClientDrawer";
import { ClientsTable } from "../components/ClientsTable";
import { NewClientModal } from "../components/NewClientModal";
import { CHIPS, filterClients, type ChipKey } from "../lib/filtering";

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

  const { data: clients = [] } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
  });

  const rows = useMemo(() => filterClients(clients, { chip, query }), [clients, chip, query]);
  const existingNames = useMemo(
    () => new Set(clients.map((client) => client.name.toLowerCase())),
    [clients],
  );

  const newClientButton = (
    <Button onClick={() => setNewClientOpen(true)} className="!px-3.5 !py-[7px] !text-[13px]">
      <Icon d={ICONS.plus} size={15} />
      New client
    </Button>
  );

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
          <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
            <div className="flex w-[300px] items-center gap-2 rounded-lg border border-line bg-panel2 px-[11px] py-[7px]">
              <Icon d={ICONS.search} size={14} className="text-text3" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search clients…"
                className="w-full bg-transparent font-mono text-[13px] text-text outline-none placeholder:text-text3"
              />
            </div>

            <div className="flex flex-wrap gap-1.5">
              {CHIPS.map(({ key, label }) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setChip(key)}
                  className={`rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-text ${
                    chip === key ? "border-amber bg-amber-dim text-amber" : "border-line text-text2"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <ClientsTable clients={rows} onOpen={setOpenClientId} />

          {rows.length === 0 && (
            <div className="p-12 text-center font-mono text-[13px] text-text3">
              No clients match. Clear the search or add a new client.
            </div>
          )}
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
          initialClientId={openClientId ?? undefined}
        />
      )}
    </>
  );
}
