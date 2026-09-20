import { Link } from "react-router-dom";
import { cn } from "../../lib/cn";
import { Icon } from "./Icon";

export interface RailNavItem {
  key: string;
  label: string;
  icon: string;
}

/**
 * The menu the UNCAVA screens hang beside their content — the report's chapters, the brief's steps.
 * One line per item, an icon and a label, the active row in a filled accent pill: the app's own
 * `Sidebar` structure in the UNCAVA tokens, so a screen on that palette reads as part of the product
 * rather than as a second navigation idiom.
 *
 * <p>Items are links, not buttons: the choice lives in the URL under `param`, so a colleague can be
 * sent straight to one and the back button walks between them. A column at `lg`, a strip below it.
 */
export function UncavaRailNav({
  label,
  param,
  items,
  activeKey,
}: {
  /** Names the menu for a screen reader. */
  label: string;
  /** The query parameter the choice lives in — `chapter`, `step`. */
  param: string;
  items: readonly RailNavItem[];
  activeKey: string;
}) {
  return (
    <nav aria-label={label} className="flex gap-0.5 overflow-x-auto lg:flex-col lg:overflow-visible">
      {items.map((item) => {
        const isActive = item.key === activeKey;
        return (
          <Link
            key={item.key}
            to={{ search: `?${param}=${item.key}` }}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "flex flex-none items-center gap-2.5 whitespace-nowrap rounded-[8px] px-2.5 py-2 text-[13px] transition",
              isActive ? "bg-u-accent-tint font-semibold text-u-accent" : "text-u-text2 hover:bg-u-raised hover:text-u-text",
            )}
          >
            <Icon d={item.icon} className="flex-none" />
            <span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
