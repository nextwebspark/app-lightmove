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
import { BusinessUnitGlyph } from "../lib/clientColumns";
import { openPositionsLabel } from "../lib/openPositions";

/**
 * The business unit drawer (`Clients.dc.html`): name and notes, the hiring managers with an inline
 * invite, and the unit's positions — one of which can be opened into a read-only sub-view without leaving.
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
    <Drawer open={clientId !== null} onClose={onClose} label={client?.name ?? "Business unit"}>
      {!client ? (
        <div className="grid flex-1 place-items-center font-mono text-[12px] text-u-text3">Loading…</div>
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
  const [notes, setNotes] = useState(client.notes ?? "");

  const dirty = name !== client.name || notes !== (client.notes ?? "");

  const save = useMutation({
    mutationFn: () =>
      clientsApi.updateClient(client.id, {
        name: name.trim(),
        // The PATCH replaces the whole record, so the fields this drawer no longer shows ride along
        // unchanged — leaving them out would clear them.
        sector: client.sector ?? undefined,
        hqCountry: client.hqCountry ?? undefined,
        domain: client.domain ?? undefined,
        offLimitsNote: client.offLimitsNote ?? undefined,
        notes: notes.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Business unit saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const discard = () => {
    setName(client.name);
    setNotes(client.notes ?? "");
  };

  return (
    <>
      <div className="relative border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3.5 top-3.5 rounded-md p-1.5 text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          ✕
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-u-text3">
          Business unit record
        </div>
        <div className="mt-1 flex items-start gap-2.5">
          <BusinessUnitGlyph size={32} />
          <div className="min-w-0">
            <div className="text-[17px] font-semibold">{client.name}</div>
            <div className="mt-0.5 font-mono text-[11px] text-u-text3">
              {openPositionsLabel(client.activeMandates)}
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <div className="flex gap-2.5">
          <StatTile value={String(client.activeMandates)} label="Open" />
          <StatTile value={String(client.deliveredMandates)} label="Filled" />
          <StatTile value={String(client.representatives.length)} label="Managers" />
        </div>

        <div className="mb-2 mt-[18px] flex items-center justify-between">
          <SectionLabel>Details</SectionLabel>
          {dirty && (
            <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-u-accent">
              unsaved
            </span>
          )}
        </div>
        <DrawerField label="Business unit name">
          <Input value={name} onChange={(event) => setName(event.target.value)} />
        </DrawerField>
        <DrawerField label="Notes">
          <textarea
            rows={3}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="e.g. hiring freeze lifted Q1, prioritise senior backfills"
            className="w-full resize-y rounded-lg border border-u-border-strong bg-u-raised px-3 py-2.5 font-mono text-[13px] text-u-text outline-none placeholder:text-u-text3 focus:border-u-accent"
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

        <SectionLabel className="mt-[18px]">Open positions</SectionLabel>
        {client.mandates.length === 0 ? (
          <p className="py-2 font-mono text-[12px] text-u-text3">
            No positions yet — open one for this business unit.
          </p>
        ) : (
          client.mandates.map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => onOpenMandate(m.id)}
              className="flex w-full items-center gap-2.5 rounded-[7px] px-2 py-2 text-left hover:bg-u-raised"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium text-u-text">{m.positionTitle}</span>
                <span className="block font-mono text-[11px] text-u-text3">Lead · {m.leadName ?? "—"}</span>
              </span>
              <StagePill stage={m.stage} />
              <span className="text-u-text3">›</span>
            </button>
          ))
        )}
      </div>

      <div className="flex items-center justify-between border-t border-u-border px-5 py-3">
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
        <Button variant="secondary" onClick={onNewMandate}>
          ＋ New position
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
      setError("Name, title and work email are required.");
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
        <SectionLabel>Hiring managers</SectionLabel>
        {!open && (
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="font-mono text-[11px] text-u-accent hover:underline"
          >
            Invite
          </button>
        )}
      </div>

      {client.representatives.length === 0 && !open && (
        <p className="py-1 font-mono text-[12px] text-u-text3">
          No hiring managers yet. Invite one to give them access to their positions.
        </p>
      )}

      {client.representatives.map((rep) => (
        <RepRow key={rep.id} rep={rep} />
      ))}

      {open && (
        <div className="mt-2 rounded-lg border border-u-border bg-u-raised p-3.5">
          {error && <p className="mb-2 font-mono text-[11px] text-u-offlimits">{error}</p>}
          <DrawerField label="Full name">
            <Input value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="e.g. Farah Nasser" />
          </DrawerField>
          <DrawerField label="Title">
            <Input value={position} onChange={(event) => setPosition(event.target.value)} placeholder="e.g. Engineering Director" />
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
  ACTIVE: { label: "Active", className: "text-u-direct bg-u-direct-tint" },
  INVITED: { label: "Invited", className: "text-u-accent bg-u-accent-tint" },
};

function RepRow({ rep }: { rep: ClientRepresentative }) {
  const badge = REP_BADGE[rep.status];
  return (
    <div className="flex items-center gap-2.5 py-1.5">
      <Avatar id={rep.id} name={rep.fullName} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px]">{rep.fullName}</div>
        <div className="truncate font-mono text-[11px] text-u-text3">
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
      <div className="border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onBack}
          className="mb-1.5 flex items-center gap-1 font-mono text-[11px] text-u-text3 hover:text-u-text"
        >
          ‹ {clientName}
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-u-text3">
          Position
        </div>
        <div className="mt-1 text-[17px] font-semibold">{mandate.positionTitle}</div>
        <div className="mt-0.5 font-mono text-[11px] text-u-text3">Lead · {mandate.leadName ?? "—"}</div>
        <Button className="mt-3 w-full" onClick={() => navigate(`/projects/${mandate.id}`)}>
          Open position →
        </Button>
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
                now ? "font-semibold text-u-accent" : done ? "text-u-text2" : "text-u-text3"
              }`}
            >
              <span
                className={`grid size-3.5 flex-none place-items-center rounded-full border-[1.5px] ${
                  done ? "border-u-direct bg-u-direct-tint" : now ? "border-u-accent" : "border-u-border-strong"
                }`}
              >
                <span className={`size-1.5 rounded-full ${done ? "bg-u-direct" : now ? "bg-u-accent" : ""}`} />
              </span>
              {stageLabel(stage)}
            </div>
          );
        })}

        <SectionLabel className="mt-[18px]">Target</SectionLabel>
        <p className="font-mono text-[12.5px] text-u-text2">{formatDate(mandate.targetDate)}</p>
      </div>

      <div className="flex items-center justify-start border-t border-u-border px-5 py-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
      </div>
    </>
  );
}

function SectionLabel({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3 ${className}`}>
      {children}
    </div>
  );
}

function DrawerField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="mb-3 block">
      <span className="mb-1 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
        {label}
      </span>
      {children}
    </label>
  );
}

function StatTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex-1 rounded-lg border border-u-border bg-u-raised px-3 py-2.5">
      <b className="block font-mono text-[17px] font-semibold text-u-text">{value}</b>
      <span className="font-mono text-[10.5px] uppercase tracking-[0.06em] text-u-text3">{label}</span>
    </div>
  );
}
