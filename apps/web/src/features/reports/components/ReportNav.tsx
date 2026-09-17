import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "../../../lib/cn";

export interface ReportChapterLink {
  key: string;
  label: string;
}

/**
 * The report's step rail: one numbered step per chapter, and where the figures came from. A column
 * beside the chapter at `lg` and a strip above it below that, because the rail is the only way
 * between chapters and cannot be hidden on a phone.
 *
 * <p>Steps are links, not buttons: the chapter lives in the URL, so a reader can send someone
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
          {chapters.map((chapter, index) => {
            const isActive = chapter.key === activeKey;
            return (
              <Link
                key={chapter.key}
                to={{ search: `?chapter=${chapter.key}` }}
                aria-current={isActive ? "step" : undefined}
                className={cn(
                  "flex flex-none items-center gap-[11px] whitespace-nowrap rounded-[8px] px-2.5 py-2 transition",
                  isActive ? "bg-u-accent-tint shadow-[inset_2px_0_0_var(--color-u-accent)]" : "hover:bg-u-raised",
                )}
              >
                <span
                  className={cn(
                    "grid size-[22px] flex-none place-items-center rounded-full text-[11px] font-bold",
                    isActive ? "bg-u-accent-solid text-white" : "border border-u-border-strong bg-u-raised text-u-text2",
                  )}
                >
                  {index + 1}
                </span>
                <span className={cn("text-[13px]", isActive && "font-semibold")}>{chapter.label}</span>
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
