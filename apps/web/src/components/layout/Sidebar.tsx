import { useEffect, useRef, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useTheme } from "../../features/theme/useTheme";
import { cn } from "../../lib/cn";
import { Icon, ICONS } from "./Icon";
import { useSidebarCollapsed } from "./useSidebarCollapsed";

export interface SidebarItem {
  to: string;
  label: string;
  icon: string;
  count?: number;
  /** Ends the NavLink match at the exact path — "/" would otherwise match everything. */
  end?: boolean;
}

export interface SidebarGroup {
  label: string;
  items: SidebarItem[];
}

/**
 * The mockups' left rail: grouped nav links with theme and collapse rows pinned to the bottom, 210px
 * wide or 56px collapsed (labels, group headers and counts disappear). On desktop it is a bare column
 * on the page ground, not a card — the main panel is the only card the mockups draw.
 *
 * <p>Below `lg` it slides in over the content as a drawer, ignoring the collapsed preference — a
 * 56px icon-only overlay would be all cost and no benefit on a phone. There it <i>is</i> a card: the
 * border, fill and shadow in the base classes are the drawer's, and the `lg:` group strips them.
 */
export function Sidebar({
  groups,
  backLink,
  header,
  open = false,
  onClose,
}: {
  groups: SidebarGroup[];
  backLink?: SidebarItem;
  /** Rendered under the back link when expanded — the project shell's stage badge lives here. */
  header?: ReactNode;
  open?: boolean;
  onClose?: () => void;
}) {
  const { collapsed, toggle } = useSidebarCollapsed();
  const { theme, toggle: toggleTheme } = useTheme();
  const dark = theme === "dark";
  const navRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    navRef.current?.focus();
    return () => previous?.focus();
  }, [open]);

  const rowClass = (extra?: string) =>
    cn(
      "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13.5px] transition",
      "hover:bg-panel2 hover:text-text",
      collapsed && "lg:justify-center",
      extra,
    );

  const labelsHidden = collapsed ? "lg:hidden" : "";

  return (
    <nav
      ref={navRef}
      id="app-nav"
      tabIndex={-1}
      aria-label="Main"
      className={cn(
        "flex flex-none flex-col overflow-y-auto overflow-x-hidden rounded-[10px] border border-line bg-panel px-2.5 py-3.5 outline-none",
        // `lg:z-auto` is load-bearing: a flex item keeps its stacking context while static, so
        // without the reset the rail floats above an open drawer's scrim instead of dimming.
        "fixed bottom-3.5 left-3.5 top-[52px] z-[95] w-60 shadow-panel transition-transform duration-200",
        open ? "translate-x-0" : "-translate-x-[calc(100%+18px)]",
        "lg:static lg:z-auto lg:translate-x-0 lg:rounded-none lg:border-0 lg:bg-transparent",
        "lg:shadow-none lg:transition-[width] lg:duration-[180ms]",
        collapsed ? "lg:w-14" : "lg:w-[210px]",
      )}
    >
      {backLink && (
        <>
          <NavLink
            to={backLink.to}
            title={backLink.label}
            className={rowClass("mb-1.5 font-medium text-text2")}
          >
            <Icon d={backLink.icon} className="flex-none" />
            <span className={cn("whitespace-nowrap", labelsHidden)}>{backLink.label}</span>
          </NavLink>
          <div className="mx-1 mb-1.5 h-px bg-line-soft" />
        </>
      )}

      {header && <div className={cn("px-2.5 pb-1 pt-0.5", labelsHidden)}>{header}</div>}

      {groups.map((group) => (
        <div key={group.label}>
          <div
            className={cn(
              "px-2.5 pb-1.5 pt-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3",
              labelsHidden,
            )}
          >
            {group.label}
          </div>
          {group.items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              title={item.label}
              className={({ isActive }) =>
                rowClass(isActive ? "bg-panel2 text-text [&_svg]:text-amber" : "text-text2")
              }
            >
              <Icon d={item.icon} className="flex-none" />
              <span className={cn("whitespace-nowrap", labelsHidden)}>{item.label}</span>
              {item.count !== undefined && (
                <span
                  className={cn(
                    "ml-auto font-mono text-[11px] font-medium text-text3",
                    labelsHidden,
                  )}
                >
                  {item.count}
                </span>
              )}
            </NavLink>
          ))}
        </div>
      ))}

      <div className="mt-auto border-t border-line-soft pt-3">
        <button
          type="button"
          onClick={toggleTheme}
          title={dark ? "Light mode" : "Dark mode"}
          className={rowClass("text-text2")}
        >
          <Icon d={dark ? ICONS.sun : ICONS.moon} className="flex-none" />
          <span className={cn("whitespace-nowrap", labelsHidden)}>
            {dark ? "Light mode" : "Dark mode"}
          </span>
        </button>
        <button
          type="button"
          onClick={toggle}
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          className={rowClass("hidden text-text2 lg:flex")}
        >
          <Icon d={collapsed ? ICONS.expand : ICONS.collapse} className="flex-none" />
          <span className={cn("whitespace-nowrap", labelsHidden)}>Collapse</span>
        </button>
        <button
          type="button"
          onClick={onClose}
          className={rowClass("text-text2 lg:hidden")}
        >
          <Icon d={ICONS.close} className="flex-none" />
          <span className="whitespace-nowrap">Close menu</span>
        </button>
      </div>
    </nav>
  );
}
