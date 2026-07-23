import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  Avatar,
  Button,
  Drawer,
  HealthDot,
  Input,
  StagePill,
  stageLabel,
  useToast,
} from "../../../components/ui";
import { isValidEmail } from "../../../lib/email";
import { messageFor } from "../../../lib/errorCodes";
import { formatDate } from "../../../lib/format";
import { STAGE_ORDER } from "../../projects/lib/filtering";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail, ClientMandate, ClientRepresentative } from "../api/types";

/**
 * The client record drawer: editable registry details, the representative list with an inline invite,
 * and the client's mandates — one of which can be opened into a read-only sub-view without leaving.
 */
export function ClientDrawer({
  clientId,
  onClose,
  onNewMandate,
}: {
  clientId: string | null;
  onClose: () => void;
  onNewMandate: () => void;
}) {
  const [mandateId, setMandateId] = useState<string | null>(null);

  // Always land on the record view: reopening a client (or switching to another) must not resurrect the
  // mandate sub-view the drawer was last left in.
  useEffect(() => {
    setMandateId(null);
  }, [clientId]);

  const { data: client } = useQuery({
    queryKey: clientsApi.clientKey(clientId ?? ""),
    queryFn: () => clientsApi.client(clientId as string),
    enabled: clientId !== null,
  });

  const mandate = client?.mandates.find((m) => m.id === mandateId) ?? null;

  return (
    <Drawer open={clientId !== null} onClose={onClose}>
      {!client ? (
        <div className="grid flex-1 place-items-center font-mono text-[12px] text-text3">Loading…</div>
      ) : mandate ? (
        <MandateView mandate={mandate} clientName={client.name} onBack={() => setMandateId(null)} />
      ) : (
        // Keyed on the client id so a refetch of the same client (e.g. after inviting a rep) does not
        // remount and clobber unsaved detail edits — only switching clients re-seeds the form.
        <ClientView
          key={client.id}
          client={client}
          onClose={onClose}
          onOpenMandate={setMandateId}
          onNewMandate={onNewMandate}
        />
      )}
    </Drawer>
  );
}

function ClientView({
  client,
  onClose,
  onOpenMandate,
  onNewMandate,
}: {
  client: ClientDetail;
  onClose: () => void;
  onOpenMandate: (mandateId: string) => void;
  onNewMandate: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [name, setName] = useState(client.name);
  const [sector, setSector] = useState(client.sector ?? "");
  const [hqCountry, setHqCountry] = useState(client.hqCountry ?? "");
  const [domain, setDomain] = useState(client.domain ?? "");
  const [offLimits, setOffLimits] = useState(client.offLimitsNote ?? "");

  const dirty =
    name !== client.name ||
    sector !== (client.sector ?? "") ||
    hqCountry !== (client.hqCountry ?? "") ||
    domain !== (client.domain ?? "") ||
    offLimits !== (client.offLimitsNote ?? "");

  const save = useMutation({
    mutationFn: () =>
      clientsApi.updateClient(client.id, {
        name: name.trim(),
        sector: sector.trim() || undefined,
        hqCountry: hqCountry.trim() || undefined,
        domain: domain.trim() || undefined,
        offLimitsNote: offLimits.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Client details saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const discard = () => {
    setName(client.name);
    setSector(client.sector ?? "");
    setHqCountry(client.hqCountry ?? "");
    setDomain(client.domain ?? "");
    setOffLimits(client.offLimitsNote ?? "");
  };

  return (
    <>
      <div className="relative border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3.5 top-3.5 rounded-md p-1.5 text-text3 hover:bg-panel2 hover:text-text"
        >
          ✕
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-text3">
          Client record
        </div>
        <div className="mt-1 text-[17px] font-semibold">{client.name}</div>
        <div className="mt-0.5 font-mono text-[11px] text-text3">
          {[client.sector, client.hqCountry, client.domain].filter(Boolean).join(" · ") || "—"}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <div className="flex gap-2.5">
          <StatTile value={String(client.activeMandates)} label="Active" />
          <StatTile value={String(client.deliveredMandates)} label="Delivered" />
          <StatTile value={String(client.representatives.length)} label="Reps" />
        </div>

        <div className="mb-2 mt-[18px] flex items-center justify-between">
          <SectionLabel>Details</SectionLabel>
          {dirty && (
            <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-amber">
              unsaved
            </span>
          )}
        </div>
        <DrawerField label="Client name">
          <Input value={name} onChange={(event) => setName(event.target.value)} />
        </DrawerField>
        <div className="flex gap-2.5">
          <div className="flex-1">
            <DrawerField label="Sector">
              <Input value={sector} onChange={(event) => setSector(event.target.value)} placeholder="e.g. FMCG" />
            </DrawerField>
          </div>
          <div className="flex-1">
            <DrawerField label="HQ">
              <Input value={hqCountry} onChange={(event) => setHqCountry(event.target.value)} placeholder="e.g. UAE" />
            </DrawerField>
          </div>
        </div>
        <DrawerField label="Domain">
          <Input value={domain} onChange={(event) => setDomain(event.target.value)} placeholder="e.g. almarai.com" />
        </DrawerField>
        <DrawerField label="Off-limits">
          <Input
            value={offLimits}
            onChange={(event) => setOffLimits(event.target.value)}
            placeholder="e.g. all employees protected until Jan 2027"
          />
        </DrawerField>
        {dirty && (
          <div className="mb-2 flex justify-end gap-2">
            <Button variant="secondary" onClick={discard}>
              Discard
            </Button>
            <Button
              loading={save.isPending}
              disabled={!name.trim()}
              onClick={() => save.mutate()}
            >
              Save changes
            </Button>
          </div>
        )}

        <Representatives client={client} />

        <SectionLabel className="mt-[18px]">Mandates</SectionLabel>
        {client.mandates.length === 0 ? (
          <p className="py-2 font-mono text-[12px] text-text3">
            No mandates yet — create a project for this client.
          </p>
        ) : (
          client.mandates.map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => onOpenMandate(m.id)}
              className="flex w-full items-center gap-2.5 rounded-[7px] px-2 py-2 text-left hover:bg-panel2"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium text-text">{m.positionTitle}</span>
                <span className="block font-mono text-[11px] text-text3">Lead · {m.leadName ?? "—"}</span>
              </span>
              <StagePill stage={m.stage} />
              <span className="text-text3">›</span>
            </button>
          ))
        )}
      </div>

      <div className="flex items-center justify-between border-t border-line-soft px-5 py-3">
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
        <Button variant="secondary" onClick={onNewMandate}>
          ＋ New mandate
        </Button>
      </div>
    </>
  );
}

function Representatives({ client }: { client: ClientDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [fullName, setFullName] = useState("");
  const [position, setPosition] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);

  const invite = useMutation({
    mutationFn: () =>
      clientsApi.inviteRepresentative(client.id, {
        fullName: fullName.trim(),
        position: position.trim(),
        email: email.trim(),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast(`Invite sent to ${email.trim()}`);
      setFullName("");
      setPosition("");
      setEmail("");
      setOpen(false);
    },
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const submit = () => {
    setError(null);
    // All three move together, matching the New-client modal and the mockup's repDraftValid.
    if (!fullName.trim() || !position.trim() || !email.trim()) {
      setError("Name, position and work email are required.");
      return;
    }
    if (!isValidEmail(email)) {
      setError("Enter a valid work email address.");
      return;
    }
    invite.mutate();
  };

  return (
    <>
      <div className="mb-2 mt-[18px] flex items-center justify-between">
        <SectionLabel>Client representatives</SectionLabel>
        {!open && (
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="font-mono text-[11px] text-sky hover:underline"
          >
            Invite
          </button>
        )}
      </div>

      {client.representatives.length === 0 && !open && (
        <p className="py-1 font-mono text-[12px] text-text3">
          No representatives yet. Invite one to give the client access to their mandates.
        </p>
      )}

      {client.representatives.map((rep) => (
        <RepRow key={rep.id} rep={rep} />
      ))}

      {open && (
        <div className="mt-2 rounded-lg border border-line-soft bg-panel2 p-3.5">
          {error && <p className="mb-2 font-mono text-[11px] text-red">{error}</p>}
          <DrawerField label="Full name">
            <Input value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="e.g. Khalid Al-Otaibi" />
          </DrawerField>
          <DrawerField label="Position">
            <Input value={position} onChange={(event) => setPosition(event.target.value)} placeholder="e.g. Group CHRO" />
          </DrawerField>
          <DrawerField label="Work email">
            <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" />
          </DrawerField>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={invite.isPending} onClick={submit}>
              Send invite
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

const REP_BADGE: Record<ClientRepresentative["status"], { label: string; className: string }> = {
  ACTIVE: { label: "Active", className: "text-green bg-green-dim" },
  INVITED: { label: "Invited", className: "text-amber bg-amber-dim" },
};

function RepRow({ rep }: { rep: ClientRepresentative }) {
  const badge = REP_BADGE[rep.status];
  return (
    <div className="flex items-center gap-2.5 py-1.5">
      <Avatar id={rep.id} name={rep.fullName} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px]">{rep.fullName}</div>
        <div className="truncate font-mono text-[11px] text-text3">
          {[rep.position, rep.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <span
        className={`rounded-md px-1.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] ${badge.className}`}
      >
        {badge.label}
      </span>
    </div>
  );
}

function MandateView({
  mandate,
  clientName,
  onBack,
}: {
  mandate: ClientMandate;
  clientName: string;
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const currentStage = STAGE_ORDER.indexOf(mandate.stage);
  const gates = STAGE_ORDER.filter((stage) => stage !== "CLOSED");

  return (
    <>
      <div className="border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onBack}
          className="mb-1.5 flex items-center gap-1 font-mono text-[11px] text-text3 hover:text-text"
        >
          ‹ {clientName}
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-text3">
          Mandate
        </div>
        <div className="mt-1 text-[17px] font-semibold">{mandate.positionTitle}</div>
        <div className="mt-0.5 font-mono text-[11px] text-text3">Lead · {mandate.leadName ?? "—"}</div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <div className="mb-4 flex items-center justify-between">
          <StagePill stage={mandate.stage} />
          <HealthDot health={mandate.health} />
        </div>

        <SectionLabel>Stage</SectionLabel>
        {gates.map((stage, index) => {
          const done = index < currentStage;
          const now = index === currentStage;
          return (
            <div
              key={stage}
              className={`flex items-center gap-2.5 py-[7px] font-mono text-[12.5px] ${
                now ? "font-semibold text-amber" : done ? "text-text2" : "text-text3"
              }`}
            >
              <span
                className={`grid size-3.5 flex-none place-items-center rounded-full border-[1.5px] ${
                  done ? "border-green bg-green-dim" : now ? "border-amber" : "border-line"
                }`}
              >
                <span className={`size-1.5 rounded-full ${done ? "bg-green" : now ? "bg-amber" : ""}`} />
              </span>
              {stageLabel(stage)}
            </div>
          );
        })}

        <SectionLabel className="mt-[18px]">Target</SectionLabel>
        <p className="font-mono text-[12.5px] text-text2">{formatDate(mandate.targetDate)}</p>
      </div>

      <div className="flex items-center justify-between border-t border-line-soft px-5 py-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
        <Button onClick={() => navigate(`/projects/${mandate.id}`)}>Open project →</Button>
      </div>
    </>
  );
}

function SectionLabel({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3 ${className}`}>
      {children}
    </div>
  );
}

function DrawerField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="mb-3 block">
      <span className="mb-1 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        {label}
      </span>
      {children}
    </label>
  );
}

function StatTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex-1 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
      <b className="block font-mono text-[17px] font-semibold text-text">{value}</b>
      <span className="font-mono text-[10.5px] uppercase tracking-[0.06em] text-text3">{label}</span>
    </div>
  );
}
