import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";

/**
 * Read-only client cards. The registry form is a separate build — the mockup's own "New client"
 * button says so with a toast; clients are born from the New-project modal.
 */
export function ClientsPage() {
  const toast = useToast();
  const { data: clients = [] } = useQuery({
    queryKey: projectsApi.CLIENTS_KEY,
    queryFn: projectsApi.clients,
  });

  return (
    <>
      <PageHeader
        title="Clients"
        subtitle="Hiring entity registry · projects inherit these records one-way"
        action={
          <Button
            variant="secondary"
            className="!px-3.5 !py-[7px] !text-[13px]"
            onClick={() => toast("Entity registry form — separate build")}
          >
            <Icon d={ICONS.plus} size={15} />
            New client
          </Button>
        }
      />

      {clients.length === 0 ? (
        <div className="p-12 text-center font-mono text-[13px] text-text3">
          No clients yet — a client record is created with your first project.
        </div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-3">
          {clients.map((client) => (
            <div key={client.id} className="rounded-[10px] border border-line-soft bg-panel2 p-3.5">
              <h4 className="mb-1 font-mono text-[13px] font-semibold">{client.name}</h4>
              <p className="font-mono text-[11.5px] text-text3">
                {client.hqCountry ? `${client.hqCountry} · ` : ""}
                {client.activeMandates} active {client.activeMandates === 1 ? "mandate" : "mandates"}
                {client.deliveredMandates > 0 && ` · ${client.deliveredMandates} delivered`}
              </p>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
