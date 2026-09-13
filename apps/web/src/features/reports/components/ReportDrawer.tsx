import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Drawer } from "../../../components/ui";
import { DrawerCloseButton } from "../../../components/ui/Drawer";

/** The shell every drill-in panel of the report shares: eyebrow, title, a mono line, then sections. */
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
  return (
    <Drawer open={open} onClose={onClose} label={title}>
      <div className="relative flex-none border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <DrawerCloseButton onClose={onClose} />
        <div className="pe-8">
          <div className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.12em] text-text3">{eyebrow}</div>
          <div className="mt-1 text-base font-semibold">{title}</div>
          <div className="mt-[3px] font-mono text-[11.5px] text-text3">{subtitle}</div>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-5 pt-1">{children}</div>
    </Drawer>
  );
}

export function DrawerNote({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-[9px] bg-panel2 px-4 py-3.5">
      <div className="mb-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-text3">{label}</div>
      <div className="text-[12.5px] leading-[1.6] text-text2">{children}</div>
    </div>
  );
}

export function DrawerLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="mt-3.5 inline-flex items-center gap-[7px] text-[13px] font-semibold text-sky hover:underline">
      <Icon d={ICONS.arrowRight} size={13} />
      {children}
    </Link>
  );
}

export function DrawerBulletRow({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-2.5 border-b border-line-soft py-2 text-[12.5px] font-semibold last:border-b-0">
      <span className="size-1.5 flex-none rounded-full bg-sky" />
      {children}
    </div>
  );
}

export function DrawerMeter({ label, pct, valueLabel, fillClass }: { label: string; pct: number; valueLabel: string; fillClass: string }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="w-[104px] flex-none text-xs text-text2">{label}</span>
      <span className="h-2 flex-1 overflow-hidden rounded-[4px] bg-line-soft">
        <span className={`block h-2 rounded-[4px] ${fillClass}`} style={{ width: `${pct}%` }} />
      </span>
      <span className="w-[104px] flex-none text-right font-mono text-[10.5px] text-text2">{valueLabel}</span>
    </div>
  );
}
