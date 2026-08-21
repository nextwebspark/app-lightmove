import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react";
import { useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
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

  const dirty =
    name !== client.name ||
    sector !== (client.sector ?? "") ||
    hqCountry !== (client.hqCountry ?? "") ||
    domain !== (client.domain ?? "");

  const save = useMutation({
    mutationFn: () =>
      clientsApi.updateClient(client.id, {
        name: name.trim(),
        sector: sector.trim() || undefined,
        hqCountry: hqCountry.trim() || undefined,
        domain: domain.trim() || undefined,
        // Off-limits has no editor on this screen yet, and the server applies whatever it is sent —
        // so the stored note is passed straight back rather than being blanked by its absence.
        offLimitsNote: client.offLimitsNote ?? undefined,
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
        <RepRow key={rep.id} clientId={client.id} rep={rep} />
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

/** The mockup's menu width. Both the placement maths and the element read it, so they agree. */
const MENU_WIDTH = 206;

/**
 * One representative, with its actions behind the row's kebab as the mockup draws them: the two ways
 * access ends are one click deep, not bare buttons sitting in the row. The wording splits on status
 * because the consequences do — an invite that was never accepted is cancelled, a live one loses
 * access it is already using.
 *
 * The menu is a native popover, so it renders in the top layer instead of inside the drawer. An
 * ordinary absolute menu is cut off by the scrolling representative list — and even `position: fixed`
 * would not escape, because the drawer's slide-in animation leaves a transform on the panel, which
 * makes it the containing block for anything fixed inside it. The top layer is above both.
 *
 * The confirmation replaces the menu's contents rather than opening a modal (again the mockup): the
 * decision is small, and the row it belongs to stays visible behind it.
 */
function RepRow({ clientId, rep }: { clientId: string; rep: ClientRepresentative }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [menu, setMenu] = useState<"closed" | "actions" | "confirm">("closed");
  const anchorRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  const badge = REP_BADGE[rep.status];
  const isInvited = rep.status === "INVITED";
  const isOpen = menu !== "closed";

  // The top layer has no anchor of its own, so the menu is placed by hand against the kebab, and
  // flipped above it when the row sits too near the bottom of the window to show it below. Before
  // paint, or the browser shows one frame of it in the corner the UA stylesheet puts it in.
  useLayoutEffect(() => {
    if (!isOpen) return;
    const place = () => {
      const anchor = anchorRef.current;
      const popover = popoverRef.current;
      if (!anchor || !popover) return;
      const rect = anchor.getBoundingClientRect();
      const height = popover.offsetHeight;
      const below = rect.bottom + 6;
      const flip = below + height > window.innerHeight - 8;
      popover.style.top = `${Math.max(8, flip ? rect.top - height - 6 : below)}px`;
      popover.style.left = `${Math.max(8, rect.right - MENU_WIDTH)}px`;
    };
    place();
    // Capture: the scroll happens on the drawer's list, which does not bubble its scroll event.
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
    // Re-placed on every state change: the confirmation is a different height from the menu it replaces.
  }, [isOpen, menu]);

  // The popover's own light dismiss closes it on Escape, but the key would carry on to the drawer's
  // handler and close that too. Swallowed here, in the capture phase, so one Escape means one step back.
  useEffect(() => {
    if (!isOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.stopPropagation();
      setMenu("closed");
    };
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [isOpen]);

  // Both the drawer and the list read this: the table's Viewers column and contact avatars are derived
  // server-side, so refreshing only the open client would leave the row behind it wrong.
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(clientId) });
    void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
  };

  const revoke = useMutation({
    mutationFn: () => clientsApi.revokeRepresentative(clientId, rep.id),
    onSuccess: () => {
      refresh();
      setMenu("closed");
      toast(isInvited ? `Invite to ${rep.email} cancelled` : `${rep.fullName} no longer has access`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const resend = useMutation({
    mutationFn: () => clientsApi.resendRepresentativeInvite(clientId, rep.id),
    onSuccess: () => {
      refresh();
      setMenu("closed");
      toast(`Invite re-sent to ${rep.email}`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const copyEmail = async () => {
    try {
      await navigator.clipboard.writeText(rep.email);
      toast("Email copied");
    } catch {
      // No clipboard permission (or no clipboard at all, over plain http) — the address is on screen
      // anyway, so say so rather than failing silently.
      toast("Could not copy — select the address instead");
    }
    setMenu("closed");
  };

  return (
    <div className="flex items-center gap-2.5 rounded-[7px] p-2 hover:bg-panel2">
      <Avatar id={rep.id} name={rep.fullName} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px]">{rep.fullName}</div>
        <div className="truncate font-mono text-[11px] text-text3">
          {[rep.position, rep.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <span
        className={`flex-none rounded-md px-1.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] ${badge.className}`}
      >
        {badge.label}
      </span>

      {/* The kebab is the popover's own invoker, so the browser toggles it. Clicking it with a plain
          handler would fight light dismiss: the pointer-down closes the menu, and the click would
          reopen it. `onClick` is only the fallback for a browser without the API. */}
      <button
        ref={anchorRef}
        type="button"
        popoverTarget={supportsPopover() ? menuId : undefined}
        popoverTargetAction="toggle"
        onClick={() => {
          if (!supportsPopover()) setMenu((state) => (state === "closed" ? "actions" : "closed"));
        }}
        aria-label={`Actions for ${rep.fullName}`}
        aria-expanded={isOpen}
        className="grid size-6 flex-none place-items-center rounded-md border border-transparent text-text3 hover:border-line hover:text-text"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="12" cy="5" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="12" cy="19" r="1.6" />
        </svg>
      </button>

      <MenuPopover
        id={menuId}
        ref={popoverRef}
        open={isOpen}
        label={`Actions for ${rep.fullName}`}
        onOpenChange={(open) => setMenu(open ? "actions" : "closed")}
      >
        {menu === "actions" ? (
          <>
            {isInvited ? (
              <button
                type="button"
                className={MENU_ITEM}
                disabled={resend.isPending}
                onClick={() => resend.mutate()}
              >
                <Icon d={ICONS.resend} size={14} className="flex-none text-text3" />
                {resend.isPending ? "Re-sending…" : "Resend invite"}
              </button>
            ) : (
              <button type="button" className={MENU_ITEM} onClick={() => void copyEmail()}>
                <Icon d={ICONS.mail} size={14} className="flex-none text-text3" />
                Copy email
              </button>
            )}
            <div className="mx-1.5 my-1 h-px bg-line-soft" />
            <button
              type="button"
              className={`${MENU_ITEM} !text-red hover:!bg-red-dim hover:!text-red`}
              onClick={() => setMenu("confirm")}
            >
              <Icon d={isInvited ? ICONS.close : ICONS.lock} size={14} className="flex-none" />
              {isInvited ? "Cancel invite" : "Revoke access"}
            </button>
          </>
        ) : (
          <div className="px-2 pb-1 pt-2">
            <div className="text-[12.5px] font-medium leading-[1.45] text-text">
              {isInvited
                ? `Cancel the invite for ${rep.fullName}? The link stops working immediately.`
                : `Revoke portal access for ${rep.fullName}? They drop off every mandate of this client.`}
            </div>
            <div className="mt-2.5 flex justify-end gap-[7px]">
              <button
                type="button"
                onClick={() => setMenu("closed")}
                className="rounded-[7px] border border-line px-2.5 py-[5px] text-[12px] font-medium text-text2 hover:border-text3 hover:text-text"
              >
                Keep
              </button>
              <button
                type="button"
                disabled={revoke.isPending}
                onClick={() => revoke.mutate()}
                className="rounded-[7px] border border-red bg-red px-2.5 py-[5px] text-[12px] font-semibold text-white hover:brightness-105 disabled:opacity-60"
              >
                {revoke.isPending ? "Working…" : isInvited ? "Cancel invite" : "Revoke"}
              </button>
            </div>
          </div>
        )}
      </MenuPopover>
    </div>
  );
}

/** Whether this browser has the popover API at all — Safari 16 and jsdom do not. */
const supportsPopover = () =>
  typeof HTMLElement !== "undefined" && "showPopover" in HTMLElement.prototype;

/**
 * The floating half of a row menu: a native popover, positioned by its caller.
 *
 * `popover="auto"` buys the top layer — no clipping by a scroll container or a transformed ancestor,
 * both of which the drawer has — plus light dismiss, which is why this file listens for no outside
 * clicks of its own. React is told about the browser's own opens and closes through `onToggle`.
 *
 * The element stays mounted so the kebab can name it as its `popoverTarget`; its contents do not, so
 * that a closed menu holds nothing for a test (or a screen reader) to find.
 */
function MenuPopover({
  id,
  open,
  label,
  onOpenChange,
  children,
  ref,
}: {
  id: string;
  open: boolean;
  label: string;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
  ref: RefObject<HTMLDivElement | null>;
}) {
  // Only for the opens and closes React decides on its own (a finished revoke, "Keep"). Both calls
  // throw if the popover is already in the state asked for, hence the check.
  useEffect(() => {
    const popover = ref.current;
    if (!popover || !supportsPopover()) return;
    const shown = popover.matches(":popover-open");
    if (open && !shown) popover.showPopover();
    if (!open && shown) popover.hidePopover();
  }, [open, ref]);

  // Left off entirely where the API is missing: the attribute alone still hides the element, under the
  // `[popover]:not(:popover-open) { display: none }` rule that ships in a default stylesheet.
  const native = supportsPopover();

  return (
    <div
      id={id}
      ref={ref}
      popover={native ? "auto" : undefined}
      aria-label={label}
      onToggle={(event) => onOpenChange(event.newState === "open")}
      style={{ width: MENU_WIDTH }}
      className="fixed m-0 rounded-[10px] border border-line bg-panel p-1.5 shadow-panel"
    >
      {open ? children : null}
    </div>
  );
}

/** The mockup's menu row: one shape for both entries, the destructive one only recoloured. */
const MENU_ITEM =
  "flex w-full items-center gap-[9px] rounded-[7px] px-2.5 py-[7px] text-left text-[12.5px] " +
  "font-medium text-text transition hover:bg-panel2 disabled:opacity-60";

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
