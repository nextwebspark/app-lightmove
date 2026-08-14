import { useEffect, useState } from "react";

export interface ReportNavItem {
  key: string;
  ordinal: string;
  label: string;
}

/**
 * The report's "On this page" rail: jump links plus a scroll-spy that tracks which section the reader
 * is in. The observer watches only the top slice of the viewport (`-45% 0px` on the bottom) so the
 * active item changes when a heading reaches reading height, rather than when a tall section's
 * *bottom* finally clears — which would leave the rail a section behind for most of the scroll.
 *
 * <p>No Export or Share buttons: neither is built, and a button that cannot work is worse than no
 * button — the reader takes it as a capability the product has.
 */
export function ReportNav({ items }: { items: ReportNavItem[] }) {
  const [activeKey, setActiveKey] = useState(items[0]?.key ?? "");

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        // Sorted by position, not taken in delivery order: entries arrive in an unspecified order and
        // carry only the sections whose intersection changed this tick, so scrolling fast past two
        // sections would otherwise leave the rail on whichever the observer happened to list first.
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible.length > 0) {
          setActiveKey(visible[0].target.id);
        }
      },
      { rootMargin: "0px 0px -45% 0px", threshold: 0 },
    );
    const sections = items
      .map((item) => document.getElementById(item.key))
      .filter((element): element is HTMLElement => element !== null);
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [items]);

  return (
    <nav className="sticky top-0 w-[200px] flex-none pt-0.5">
      <div className="px-2.5 pb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
        On this page
      </div>
      {items.map((item) => {
        const active = item.key === activeKey;
        return (
          <a
            key={item.key}
            href={`#${item.key}`}
            aria-current={active ? "true" : undefined}
            className={`flex w-full items-baseline gap-[9px] rounded-[7px] px-2.5 py-[7px] text-left text-[12.5px] font-medium transition ${
              active ? "bg-panel2 text-text" : "text-text2 hover:bg-panel2 hover:text-text"
            }`}
          >
            <b
              className={`flex-none font-mono text-[10px] font-semibold tracking-[0.04em] ${
                active ? "text-sky" : "text-text3"
              }`}
            >
              {item.ordinal}
            </b>
            <span>{item.label}</span>
          </a>
        );
      })}
    </nav>
  );
}
