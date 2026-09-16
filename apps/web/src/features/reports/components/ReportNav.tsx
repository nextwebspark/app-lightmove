import { useEffect, useState } from "react";
import { cn } from "../../../lib/cn";

export interface ReportNavItem {
  key: string;
  ordinal: string;
  label: string;
}

/**
 * The report's "On this page" rail: jump links plus a scroll-spy that tracks which chapter the reader
 * is in. The observer watches only the top slice of the viewport (`-45% 0px` on the bottom) so the
 * active item changes when a heading reaches reading height, rather than when a tall chapter's
 * *bottom* finally clears — which would leave the rail a chapter behind for most of the scroll.
 *
 * <p>No Export or Share buttons: neither is built, and a button that cannot work is worse than no
 * button — the reader takes it as a capability the product has.
 */
export function ReportNav({ items, footer }: { items: ReportNavItem[]; footer?: string }) {
  const [activeKey, setActiveKey] = useState(items[0]?.key ?? "");

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        // Sorted by position, not taken in delivery order: entries arrive in an unspecified order and
        // carry only the chapters whose intersection changed this tick, so scrolling fast past two
        // would otherwise leave the rail on whichever the observer happened to list first.
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible.length > 0) setActiveKey(visible[0].target.id);
      },
      { rootMargin: "0px 0px -45% 0px", threshold: 0 },
    );
    items
      .map((item) => document.getElementById(item.key))
      .filter((element): element is HTMLElement => element !== null)
      .forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [items]);

  return (
    <nav className="sticky top-0 hidden w-[200px] flex-none pt-0.5 lg:block" aria-label="On this page">
      <div className="px-2.5 pb-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">
        On this page
      </div>
      {items.map((item) => {
        const isActive = item.key === activeKey;
        return (
          <a
            key={item.key}
            href={`#${item.key}`}
            aria-current={isActive ? "true" : undefined}
            className={cn(
              "flex w-full items-baseline gap-[9px] rounded-[7px] px-2.5 py-[7px] text-left text-[12.5px] font-medium transition",
              isActive ? "bg-u-raised text-u-text" : "text-u-text2 hover:bg-u-raised hover:text-u-text",
            )}
          >
            <b
              className={cn(
                "flex-none font-u-num text-[10px] font-semibold tracking-[0.04em]",
                isActive ? "text-u-accent" : "text-u-text3",
              )}
            >
              {item.ordinal}
            </b>
            <span>{item.label}</span>
          </a>
        );
      })}
      {footer && (
        <>
          <div className="mx-2.5 my-3 h-px bg-u-border" />
          <div className="px-2.5 font-u-num text-[11px] leading-[1.6] text-u-text3">{footer}</div>
        </>
      )}
    </nav>
  );
}
