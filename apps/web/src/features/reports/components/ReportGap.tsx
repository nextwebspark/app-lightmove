import type { ReactNode } from "react";
import { Icon } from "../../../components/layout/Icon";

/** A place where the report has nothing to show, named rather than left blank. */
export function ReportGap({ icon, title, children }: { icon: string; title: string; children?: ReactNode }) {
  return (
    <div className="px-2.5 py-10 text-center">
      <Icon d={icon} size={28} className="mx-auto mb-3.5 text-u-text3" />
      <div className="text-sm font-bold">{title}</div>
      {children && <div className="mt-4 text-left">{children}</div>}
    </div>
  );
}
