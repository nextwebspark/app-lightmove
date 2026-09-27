import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Drawer,
  HealthDot,
  Input,
  StagePill,
  stageLabel,
  TextArea,
  useToast,
} from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatDate } from "../../../lib/format";
import { STAGE_ORDER } from "../../projects/lib/filtering";
import { useWorkspaceMode, useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail, ClientMandate } from "../api/types";
import { openPositionsLabel } from "../lib/openPositions";
import { AgencyClientView } from "./AgencyClientView";
import { ClientMark } from "./ClientMark";
import { ClientDrawerFoot, ClientPositions, DrawerField, Representatives, SectionLabel } from "./ClientRecordParts";

/**
 * The client record drawer (`Clients.dc.html`): an agency's client as a company panel, an in-house
 * business unit as its record — either way its people with an inline invite, and its positions, one of
 * which can be opened into a read-only sub-view without leaving.
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
  const vocabulary = useWorkspaceVocabulary();
  const RecordView = useWorkspaceMode() === "AGENCY" ? AgencyClientView : ClientRecordView;
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
    <Drawer open={clientId !== null} onClose={onClose} label={client?.name ?? vocabulary.unit}>
      {!client ? (
        <div className="grid flex-1 place-items-center font-mono text-[12px] text-u-text3">Loading…</div>
      ) : mandate ? (
        <MandateView mandate={mandate} clientName={client.name} onBack={() => setMandateId(null)} />
      ) : (
        // Keyed on the client id so a refetch of the same client (e.g. after inviting a rep) does not
        // remount and clobber unsaved detail edits — only switching clients re-seeds the form.
        <RecordView
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

function ClientRecordView({
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
  const vocabulary = useWorkspaceVocabulary();

  const [name, setName] = useState(client.name);
  const [notes, setNotes] = useState(client.notes ?? "");

  const dirty = name !== client.name || notes !== (client.notes ?? "");

  const save = useMutation({
    mutationFn: () =>
      clientsApi.updateClient(client.id, {
        name: name.trim(),
        notes: notes.trim(),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast(`${vocabulary.unit} saved`);
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
          {vocabulary.unit} record
        </div>
        <div className="mt-1 flex items-start gap-2.5">
          <ClientMark name={client.name} logoUrl={client.logoUrl} size={32} />
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
        <DrawerField label={`${vocabulary.unit} name`}>
          <Input value={name} onChange={(event) => setName(event.target.value)} />
        </DrawerField>
        <DrawerField label="Notes">
          <TextArea
            rows={3}
            maxLength={2000}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="e.g. hiring freeze lifted Q1, prioritise senior backfills"
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

        <ClientPositions client={client} onOpenMandate={onOpenMandate} />
      </div>

      <ClientDrawerFoot onClose={onClose} onNewMandate={onNewMandate} />
    </>
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

      <div className="flex items-center border-t border-u-border px-5 py-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
      </div>
    </>
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
