import { initials } from "../../../lib/format";
import type { Client, ClientRepStatus, ClientType, ViewerSummary } from "../api/types";

/**
 * The client registry: a table on a wide screen, and a stack of cards below `md`.
 */
export function ClientsList({
  clients,
  onOpen,
}: {
  clients: Client[];
  onOpen: (clientId: string) => void;
}) {
  const th =
    "whitespace-nowrap border-b border-line px-3 py-[9px] text-left font-mono text-[10.5px] " +
    "font-semibold uppercase tracking-[0.12em] text-text3";
  const td = "border-b border-line-soft px-3 py-[11px]";
  const pinned = "sticky start-0 z-[1] bg-panel group-hover:bg-panel2";

  return (
    <>
      <div className="flex flex-col gap-2.5 md:hidden">
        {clients.map((client) => (
          <ClientCard key={client.id} client={client} onOpen={() => onOpen(client.id)} />
        ))}
      </div>

      <div className="hidden overflow-x-auto md:block">
      <table className="w-full min-w-[820px] border-collapse">
      <thead>
        <tr>
          <th className={`${th} ${pinned}`}>Client</th>
          <th className={th}>Type</th>
          <th className={th}>Client contact</th>
          <th className={th}>Sector</th>
          <th className={th}>Mandates</th>
          <th className={th}>Viewers</th>
        </tr>
      </thead>
      <tbody>
        {clients.map((client) => (
          <tr
            key={client.id}
            className="group cursor-pointer hover:bg-panel2"
            onClick={() => onOpen(client.id)}
          >
            <td className={`${td} ${pinned} whitespace-nowrap`}>
              <span className="flex items-center gap-2.5">
                <span className="grid size-6 flex-none place-items-center rounded-md bg-amber-dim font-mono text-[10px] font-semibold text-amber">
                  {initials(client.name)}
                </span>
                <span className="text-[13px] font-semibold text-text">{client.name}</span>
              </span>
            </td>
            <td className={td}>
              <TypePill type={client.type} />
            </td>
            <td className={td}>
              <RepStack contacts={client.contacts} />
            </td>
            <td className={`${td} whitespace-nowrap font-mono text-xs text-text2`}>
              {client.sector ?? "—"}
            </td>
            <td className={`${td} whitespace-nowrap font-mono text-xs text-text2`}>
              {client.activeMandates}
            </td>
            <td className={td}>
              <ViewerCell viewers={client.viewers} />
            </td>
          </tr>
        ))}
      </tbody>
        </table>
      </div>
    </>
  );
}

/** One client as a card: the same fields the row carries, stacked for a thumb. */
function ClientCard({ client, onOpen }: { client: Client; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex w-full flex-col gap-2.5 rounded-[10px] border border-line bg-panel p-3.5 text-left transition hover:bg-panel2"
    >
      <div className="flex items-start gap-2.5">
        <span className="grid size-7 flex-none place-items-center rounded-md bg-amber-dim font-mono text-[10px] font-semibold text-amber">
          {initials(client.name)}
        </span>
        <span className="min-w-0 flex-1 text-[13.5px] font-semibold text-text">{client.name}</span>
        <span className="flex-none">
          <TypePill type={client.type} />
        </span>
      </div>

      <div className="font-mono text-[11.5px] text-text3">
        {[client.sector, client.hqCountry].filter(Boolean).join(" · ") || "—"}
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

const TYPE_STYLES: Record<ClientType, { label: string; className: string }> = {
  RETAINED: { label: "Retained", className: "text-sky bg-sky-dim border-transparent" },
  PROSPECT: { label: "Prospect", className: "text-text3 border-line-soft" },
};

function TypePill({ type }: { type: ClientType }) {
  const { label, className } = TYPE_STYLES[type];
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-md border px-[9px] py-[3px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${className}`}
    >
      {label}
    </span>
  );
}

const REP_TINT: Record<ClientRepStatus, string> = {
  ACTIVE: "bg-green-dim text-green",
  INVITED: "bg-amber-dim text-amber",
};

function RepStack({ contacts }: { contacts: { fullName: string; status: ClientRepStatus }[] }) {
  if (contacts.length === 0) {
    return <span className="font-mono text-xs text-text3">—</span>;
  }
  const shown = contacts.slice(0, 4);
  const overflow = contacts.length - shown.length;
  return (
    <span className="flex items-center">
      {shown.map((contact, index) => (
        <span
          key={`${contact.fullName}-${index}`}
          title={contact.fullName}
          className={`grid size-6 place-items-center rounded-full border-2 border-panel font-mono text-[10px] font-semibold ${
            REP_TINT[contact.status]
          } ${index > 0 ? "-ml-[7px]" : ""}`}
        >
          {initials(contact.fullName)}
        </span>
      ))}
      {overflow > 0 && (
        <span className="-ml-[7px] grid size-6 place-items-center rounded-full border-2 border-panel bg-panel2 font-mono text-[10px] font-semibold text-text3">
          +{overflow}
        </span>
      )}
    </span>
  );
}

function ViewerCell({ viewers }: { viewers: ViewerSummary }) {
  const dot = viewers.active > 0 ? "bg-green" : viewers.invited > 0 ? "bg-amber" : "bg-line";
  const label =
    viewers.active === 0 && viewers.invited === 0
      ? "None"
      : [
          viewers.active > 0 ? `${viewers.active} active` : null,
          viewers.invited > 0 ? `${viewers.invited} invited` : null,
        ]
          .filter(Boolean)
          .join(" · ");
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap font-mono text-xs text-text2">
      <span className={`size-[7px] rounded-full ${dot}`} />
      {label}
    </span>
  );
}
