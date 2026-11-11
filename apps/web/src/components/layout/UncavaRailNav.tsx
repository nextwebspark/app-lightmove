import { Link } from "react-router-dom";
import { cn } from "../../lib/cn";
import { Icon } from "./Icon";

export interface RailNavItem {
  key: string;
  label: string;
  icon: string;
  /** A small trailing note — the brief's "N filled" after a document reading. Absent everywhere else. */
  badge?: string;
}

/**
 * The UNCAVA screens' side menu (report chapters, brief steps), drawn like `Sidebar`. Selected: sunken ground,
 * primary ink, accent on the icon only (accent tint read as a chip). Items are links under `param`.
 */
export function UncavaRailNav({
  label,
  param,
  items,
  activeKey,
  collapsed = false,
}: {
  /** Names the menu for a screen reader. */
  label: string;
  /** The query parameter the choice lives in — `chapter`, `step`. */
  param: string;
  items: readonly RailNavItem[];
  activeKey: string;
  collapsed?: boolean;
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
            aria-label={collapsed ? item.label : undefined}
            title={collapsed ? item.label : undefined}
            className={cn(
              "flex flex-none items-center gap-2.5 whitespace-nowrap rounded-[7px] px-2.5 py-2 text-body transition",
              isActive
                ? "bg-u-sunken font-medium text-u-text [&_svg]:text-u-accent"
                : "text-u-text2 hover:bg-u-raised hover:text-u-text",
              collapsed && "lg:justify-center",
            )}
          >
            <Icon d={item.icon} className="flex-none" />
            <span className={cn("flex-1", collapsed && "lg:hidden")}>{item.label}</span>
            {item.badge && (
              <span
                className={cn(
                  "flex-none rounded-full bg-u-inferred-tint px-1.5 py-0.5 text-eyebrow font-semibold text-u-inferred",
                  collapsed && "lg:hidden",
                )}
              >
                {item.badge}
              </span>
            )}
          </Link>
        );
      })}
    </nav>
  );
}
