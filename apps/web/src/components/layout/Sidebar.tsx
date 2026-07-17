import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useTheme } from "../../features/theme/useTheme";
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
 * The mockups' left rail: a rounded panel of grouped nav links, theme and collapse rows pinned to
 * the bottom, 240px wide or 56px collapsed (labels, group headers and counts disappear).
 */
export function Sidebar({
  groups,
  backLink,
  header,
}: {
  groups: SidebarGroup[];
  backLink?: SidebarItem;
  /** Rendered under the back link when expanded — the project shell's stage badge lives here. */
  header?: ReactNode;
}) {
  const { collapsed, toggle } = useSidebarCollapsed();
  const { theme, toggle: toggleTheme } = useTheme();
  const dark = theme === "dark";

  const rowClass =
    "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13.5px] " +
    "transition hover:bg-panel2 hover:text-text " +
    (collapsed ? "justify-center" : "");

  return (
    <nav
      className={`flex flex-none flex-col overflow-y-auto overflow-x-hidden rounded-[10px] border border-line bg-panel px-2 py-3.5 transition-[width] duration-150 ${collapsed ? "w-14" : "w-60"}`}
    >
      {backLink && (
        <>
          <NavLink to={backLink.to} title={backLink.label} className={`${rowClass} mb-1.5 font-medium text-text2`}>
            <Icon d={backLink.icon} className="flex-none" />
            {!collapsed && <span className="whitespace-nowrap">{backLink.label}</span>}
          </NavLink>
          <div className="mx-1 mb-1.5 h-px bg-line-soft" />
        </>
      )}

      {header && !collapsed && <div className="px-2.5 pb-1 pt-0.5">{header}</div>}

      {groups.map((group) => (
        <div key={group.label}>
          {!collapsed && (
            <div className="px-2.5 pb-1.5 pt-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
              {group.label}
            </div>
          )}
          {group.items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              title={item.label}
              className={({ isActive }) =>
                `${rowClass} ${isActive ? "bg-panel2 text-text [&_svg]:text-amber" : "text-text2"}`
              }
            >
              <Icon d={item.icon} className="flex-none" />
              {!collapsed && (
                <>
                  <span className="whitespace-nowrap">{item.label}</span>
                  {item.count !== undefined && (
                    <span className="ml-auto font-mono text-[11px] font-medium text-text3">
                      {item.count}
                    </span>
                  )}
                </>
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
          className={`${rowClass} text-text2`}
        >
          <Icon d={dark ? ICONS.sun : ICONS.moon} className="flex-none" />
          {!collapsed && <span className="whitespace-nowrap">{dark ? "Light mode" : "Dark mode"}</span>}
        </button>
        <button
          type="button"
          onClick={toggle}
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          className={`${rowClass} text-text2`}
        >
          <Icon d={collapsed ? ICONS.expand : ICONS.collapse} className="flex-none" />
          {!collapsed && <span className="whitespace-nowrap">Collapse</span>}
        </button>
      </div>
    </nav>
  );
}
