import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { UncavaRailNav, type RailNavItem } from "../../../components/layout/UncavaRailNav";
import { cn } from "../../../lib/cn";
import { useReportNavCollapsed } from "../lib/useReportNavCollapsed";

export type ReportChapterLink = RailNavItem;

/**
 * The report's chapter menu (`UncavaRailNav`): a column at `lg`, a strip below, never hidden. No Export or Share:
 * neither is built, and a button that cannot work reads as a capability.
 */
export function ReportNav({
  chapters,
  activeKey,
  footer,
}: {
  chapters: readonly ReportChapterLink[];
  activeKey: string;
  footer: ReactNode;
}) {
  const { collapsed, toggle } = useReportNavCollapsed();
  return (
    <aside
      className={cn(
        "flex-none border-b border-u-border transition-[width] duration-200 lg:border-b-0 lg:border-r",
        collapsed ? "lg:w-14" : "lg:w-[258px]",
      )}
    >
      <div
        className={cn(
          "px-4 py-3.5 lg:sticky lg:top-0 lg:py-[30px]",
          collapsed ? "lg:px-2" : "lg:px-[22px]",
        )}
      >
        <UncavaRailNav
          label="Report chapters"
          param="chapter"
          items={chapters}
          activeKey={activeKey}
          collapsed={collapsed}
        />
        <div
          className={cn(
            "mt-3 text-[11px] italic leading-[1.7] text-u-text3 lg:mt-7 lg:border-t lg:border-u-border lg:pt-5",
            collapsed && "lg:hidden",
          )}
        >
          {footer}
        </div>
        <button
          type="button"
          onClick={toggle}
          aria-label={collapsed ? "Expand chapters" : "Collapse chapters"}
          title={collapsed ? "Expand chapters" : "Collapse chapters"}
          className={cn(
            "mt-4 hidden w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-body text-u-text2 transition hover:bg-u-raised hover:text-u-text lg:flex",
            collapsed && "justify-center",
          )}
        >
          <Icon d={collapsed ? ICONS.expand : ICONS.collapse} className="flex-none" />
          <span className={cn("whitespace-nowrap", collapsed && "hidden")}>Collapse</span>
        </button>
      </div>
    </aside>
  );
}
