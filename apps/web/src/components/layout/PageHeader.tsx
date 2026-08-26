import type { ReactNode } from "react";

/** The mockups' page head: 19px title, muted mono subtitle, actions pushed to the right. */
export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-[18px] flex flex-col gap-3 sm:flex-row sm:items-start sm:gap-4">
      <div>
        <div className="text-[19px] font-semibold leading-tight">{title}</div>
        {subtitle && <div className="mt-1 font-mono text-xs text-text3">{subtitle}</div>}
      </div>
      {action && <div className="flex flex-wrap gap-2 sm:ml-auto">{action}</div>}
    </div>
  );
}
