import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Avatar, Button, Input, StagePill, useToast } from "../../../components/ui";
import { isValidEmail } from "../../../lib/email";
import { messageFor } from "../../../lib/errorCodes";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail, ClientRepresentative } from "../api/types";

/** The parts of a client record both drawers draw: its people, its positions, and the drawer's foot. */

export function ClientPositions({
  client,
  onOpenMandate,
}: {
  client: ClientDetail;
  onOpenMandate: (mandateId: string) => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  return (
    <>
      <SectionLabel className="mt-[18px]">Open positions</SectionLabel>
      {client.mandates.length === 0 ? (
        <p className="py-2 font-mono text-[12px] text-u-text3">
          No positions yet — open one for this {vocabulary.unitLower}.
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
    </>
  );
}

export function ClientDrawerFoot({ onClose, onNewMandate }: { onClose: () => void; onNewMandate: () => void }) {
  return (
    <div className="flex items-center justify-between border-t border-u-border px-5 py-3">
      <Button variant="ghost" onClick={onClose}>
        Close
      </Button>
      <Button variant="secondary" onClick={onNewMandate}>
        ＋ New position
      </Button>
    </div>
  );
}

export function Representatives({ client }: { client: ClientDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const vocabulary = useWorkspaceVocabulary();
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
        <SectionLabel>{vocabulary.contacts}</SectionLabel>
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
          No {vocabulary.contactsLower} yet. Invite one to give them access to their positions.
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

export function SectionLabel({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3 ${className}`}>
      {children}
    </div>
  );
}

export function DrawerField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="mb-3 block">
      <span className="mb-1 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
        {label}
      </span>
      {children}
    </label>
  );
}
