import type { ReactNode } from "react";
import { UncavaRailNav, type RailNavItem } from "../../../components/layout/UncavaRailNav";

export type ReportChapterLink = RailNavItem;

/**
 * The report's chapter menu, and where the figures came from. A column beside the chapter at `lg`
 * and a strip above it below that, because the menu is the only way between chapters and cannot be
 * hidden on a phone. The menu itself is `UncavaRailNav`, which the brief's rail shares.
 *
 * <p>No Export or Share buttons: neither is built, and a button that cannot work is worse than no
 * button — the reader takes it as a capability the product has.
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
  return (
    <aside className="flex-none border-b border-u-border lg:w-[258px] lg:border-b-0 lg:border-r">
      <div className="px-4 py-3.5 lg:sticky lg:top-0 lg:px-[22px] lg:py-[30px]">
        <UncavaRailNav label="Report chapters" param="chapter" items={chapters} activeKey={activeKey} />
        <div className="mt-3 text-[11px] italic leading-[1.7] text-u-text3 lg:mt-7 lg:border-t lg:border-u-border lg:pt-5">
          {footer}
        </div>
      </div>
    </aside>
  );
}
