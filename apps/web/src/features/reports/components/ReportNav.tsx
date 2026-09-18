import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

export interface ReportChapterLink {
  key: string;
  label: string;
  icon: string;
}

/**
 * The report's chapter menu, and where the figures came from. A column beside the chapter at `lg`
 * and a strip above it below that, because the menu is the only way between chapters and cannot be
 * hidden on a phone.
 *
 * <p>Built like the app's own rail — an icon, a label, and the active row in a filled pill — so the
 * report reads as a screen of the product rather than as a second navigation idiom. It is the app's
 * `Sidebar` structure in the report's UNCAVA tokens, not that component: the two palettes differ,
 * and the rail's collapse, groups and theme row have nothing to do with a chapter list.
 *
 * <p>Chapters are links, not buttons: the chapter lives in the URL, so a reader can send someone
 * straight to Remuneration and the back button walks the chapters.
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
        <nav aria-label="Report chapters" className="flex gap-0.5 overflow-x-auto lg:flex-col lg:overflow-visible">
          {chapters.map((chapter) => {
            const isActive = chapter.key === activeKey;
            return (
              <Link
                key={chapter.key}
                to={{ search: `?chapter=${chapter.key}` }}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "flex flex-none items-center gap-2.5 whitespace-nowrap rounded-[8px] px-2.5 py-2 text-[13px] transition",
                  isActive
                    ? "bg-u-accent-tint font-semibold text-u-accent"
                    : "text-u-text2 hover:bg-u-raised hover:text-u-text",
                )}
              >
                <Icon d={chapter.icon} className="flex-none" />
                <span>{chapter.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className="mt-3 text-[11px] italic leading-[1.7] text-u-text3 lg:mt-7 lg:border-t lg:border-u-border lg:pt-5">
          {footer}
        </div>
      </div>
    </aside>
  );
}
