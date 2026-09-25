import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { initials } from "../../../lib/format";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import type { CandidateStatus } from "../../candidates/api/types";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import { STATUS_TONES, statusTone } from "../lib/statusTone";

/**
 * The drill-in panel every chapter opens: anchored to the edge of the screen and full height, where
 * the app's drawer floats inset from it.
 *
 * <p>Portalled to the body: a chapter's `animate-fade-up` leaves a transform on its `<section>`, which
 * makes it the containing block for `position: fixed`, so a drawer opened from a scrolled chapter
 * was pinned to the top of the chapter — above the viewport — and clipped to its column.
 */
export function ReportDrawer({
  open,
  onClose,
  eyebrow,
  title,
  subtitle,
  children,
}: {
  open: boolean;
  onClose: () => void;
  eyebrow: string;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  useEscapeKey(open, onClose);

  if (!open) return null;

  return createPortal(
    <>
      <div className="fixed inset-0 z-[90] bg-u-scrim" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="fixed inset-y-0 right-0 z-[95] flex w-[384px] max-w-[92vw] animate-slide-in-end flex-col border-l border-u-border bg-u-surface text-u-text shadow-u-e3"
      >
        <div className="flex flex-none items-start justify-between gap-2.5 border-b border-u-border px-6 py-[22px]">
          <div className="min-w-0">
            <div className="text-[9.5px] font-bold uppercase tracking-[0.1em] text-u-text3">{eyebrow}</div>
            <div className="mt-[5px] text-[19px] font-bold">{title}</div>
            <div className="mt-[3px] text-xs text-u-text3">{subtitle}</div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="grid size-[30px] flex-none place-items-center rounded-[7px] border border-u-border-strong bg-u-surface text-u-text2 transition hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={15} />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-[22px]">{children}</div>
      </aside>
    </>,
    document.body,
  );
}

export function DrawerSection({ label, children }: { label?: string; children: ReactNode }) {
  return (
    <section className="mb-6">
      {label && <h3 className="mb-2.5 text-[10px] font-bold uppercase tracking-[0.08em] text-u-text3">{label}</h3>}
      {children}
    </section>
  );
}

export function DrawerKpis({ columns = 3, children }: { columns?: 2 | 3; children: ReactNode }) {
  return <div className={cn("grid gap-2.5", columns === 2 ? "grid-cols-2" : "grid-cols-3")}>{children}</div>;
}

export function DrawerKpi({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div className="min-w-0 rounded-[9px] border border-u-border bg-u-raised p-3">
      <div className="break-words font-u-num text-[19px] font-extrabold leading-[1.2]">{value}</div>
      <div className="mt-1 text-[9px] font-bold uppercase tracking-[0.05em] text-u-text3">{label}</div>
    </div>
  );
}

/** A label and its value on one hairlined line. */
export function DrawerRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-u-border py-[9px] text-[12.5px] last:border-b-0">
      <span>{label}</span>
      <b className="text-right font-bold">{children}</b>
    </div>
  );
}

/** One name in a plain list — a contributing company, an employer. */
export function DrawerListRow({ children }: { children: ReactNode }) {
  return <div className="border-b border-u-border py-[9px] text-[12.5px] font-semibold last:border-b-0">{children}</div>;
}

export function DrawerPersonRow({ name, detail, status }: { name: string; detail: string; status: CandidateStatus }) {
  return (
    <div className="flex items-center gap-[11px] border-b border-u-border py-[9px] last:border-b-0">
      <span className="grid size-7 flex-none place-items-center rounded-full border border-u-border-strong bg-u-raised text-[10px] font-bold text-u-text2">
        {initials(name)}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[12.5px] font-semibold">{name}</span>
        <span className="mt-px block text-[10.5px] text-u-text3">{detail}</span>
      </span>
      <StatusPill status={status} />
    </div>
  );
}

export function StatusPill({ status }: { status: CandidateStatus }) {
  return (
    <span
      className={cn(
        "flex-none whitespace-nowrap rounded-full px-[9px] py-[3px] text-[9.5px] font-bold",
        STATUS_TONES[statusTone(status)].pill,
      )}
    >
      {candidateStatusStyle(status).label}
    </span>
  );
}

export function DrawerContext({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-[9px] bg-u-sunken px-4 py-3.5">
      <div className="mb-1.5 text-[9.5px] font-bold uppercase tracking-[0.07em] text-u-text3">{label}</div>
      <div className="text-xs leading-[1.6] text-u-text2">{children}</div>
    </div>
  );
}

export function DrawerLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="mt-5 inline-flex items-center gap-[7px] text-[13px] font-semibold text-u-signal hover:underline">
      <Icon d={ICONS.arrowRight} size={13} />
      {children}
    </Link>
  );
}

export function DrawerMeter({ label, pct, valueLabel, fillClass }: { label: string; pct: number; valueLabel: string; fillClass: string }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="w-[108px] flex-none text-[11.5px] text-u-text2">{label}</span>
      <span className="h-2 flex-1 overflow-hidden rounded-[4px] bg-u-sunken">
        <span className={cn("block h-full rounded-[4px]", fillClass)} style={{ width: `${pct}%` }} />
      </span>
      <span className="w-[84px] flex-none text-right font-u-num text-[10.5px]">{valueLabel}</span>
    </div>
  );
}
