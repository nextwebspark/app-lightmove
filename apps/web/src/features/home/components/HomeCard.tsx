import type { ReactNode } from "react";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** One of Home's three cards: a heading with its aside, the body, and an optional foot. */
export function HomeCard({
  title,
  badge,
  subtitle,
  aside,
  footer,
  className,
  children,
}: {
  title: string;
  badge?: ReactNode;
  subtitle?: ReactNode;
  aside?: ReactNode;
  footer?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section
      aria-label={title}
      className={cn("flex min-w-0 flex-col overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface", className)}
    >
      <div className="flex flex-wrap items-start gap-x-3 gap-y-2 px-5 pb-3 pt-4">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lead font-semibold">
            {title}
            {badge}
          </h2>
          {subtitle && <div className="mt-0.5 text-note text-u-text3">{subtitle}</div>}
        </div>
        {aside && <div className="ml-auto flex items-center gap-3">{aside}</div>}
      </div>
      <div className="min-h-0 flex-1">{children}</div>
      {footer && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 border-t border-u-border px-5 py-3 text-note">
          {footer}
        </div>
      )}
    </section>
  );
}

export function CountBadge({ count }: { count: string }) {
  return (
    <span className="rounded-u-control bg-u-accent-tint px-1.5 py-px font-mono text-meta font-semibold text-u-accent">
      {count}
    </span>
  );
}

/** A card with nothing to show: why, in a line, and the one thing that would fill it. */
export function CardEmpty({
  icon,
  title,
  body,
  action,
}: {
  icon: string;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 pb-7 pt-4 text-center">
      <span aria-hidden="true" className="mb-3 grid size-10 place-items-center rounded-u-card bg-u-accent-tint text-u-accent">
        <Icon d={icon} size={18} />
      </span>
      <p className="text-body font-semibold">{title}</p>
      <p className="mt-1 max-w-sm text-note text-u-text3">{body}</p>
      {action && <div className="mt-3.5 flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  );
}

/** A one-line nudge above a card's list: something missing that would put more on it. */
export function CardNudge({ icon, children, action }: { icon: string; children: ReactNode; action: ReactNode }) {
  return (
    <div className="mx-5 mb-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-u-chip border border-u-border bg-u-raised px-3.5 py-2.5 text-note text-u-text2">
      <Icon d={icon} size={14} className="flex-none text-u-text3" />
      <span className="min-w-0 flex-1">{children}</span>
      {action}
    </div>
  );
}
