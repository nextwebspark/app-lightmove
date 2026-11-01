import { useState } from "react";
import { NavLink } from "react-router-dom";
import { cn } from "../../lib/cn";
import { useHelp } from "../../features/help/HelpProvider";
import { APP_VERSION } from "../../lib/version";
import { Icon, ICONS } from "./Icon";
import { useSidebarCollapsed } from "./useSidebarCollapsed";

export interface SidebarItem {
  to: string;
  label: string;
  icon: string;
  count?: number;
  /** Ends the NavLink match at the exact path — "/" would otherwise match everything. */
  end?: boolean;
  /** The tooltip, where the label alone is shorter than what the link promises. */
  title?: string;
  /** Said of an item that leads out of the shell it sits in, which it marks with an arrow. */
  leavesShell?: string;
}

export interface SidebarGroup {
  label: string;
  items: SidebarItem[];
}

/**
 * The mockups' left rail: the way back pinned to the top, the grouped nav links scrolling between, and the collapse
 * row pinned to the bottom; 210px wide or 56px collapsed (labels, group headers and counts disappear). On desktop it
 * is a bare column on the page ground, not a card — the main panel is the only card the mockups draw.
 *
 * <p>It is drawn from `lg` only: below it the bottom bar's More opens the same screens in a panel.
 *
 * <p>`assistantOpen` collapses it without touching the stored preference, so shutting the assistant
 * restores whatever the user had chosen.
 */
export function Sidebar({
  groups,
  backLink,
  assistantOpen = false,
}: {
  groups: SidebarGroup[];
  backLink?: SidebarItem;
  assistantOpen?: boolean;
}) {
  const preference = useSidebarCollapsed();
  const { openHelp } = useHelp();
  const [expandedBesideAssistant, setExpandedBesideAssistant] = useState(false);
  const [assistantWasOpen, setAssistantWasOpen] = useState(assistantOpen);
  if (assistantWasOpen !== assistantOpen) {
    setAssistantWasOpen(assistantOpen);
    setExpandedBesideAssistant(false);
  }
  const collapsed = assistantOpen ? !expandedBesideAssistant : preference.collapsed;
  const toggle = assistantOpen ? () => setExpandedBesideAssistant((expanded) => !expanded) : preference.toggle;
  const rowClass = (extra?: string) => railRowClass(collapsed, extra);
  const labelsHidden = collapsed ? "hidden" : "";

  return (
    <nav
      id="app-nav"
      aria-label="Main"
      className={cn(
        // No z-index of its own: a rail with one floats above an open drawer's scrim instead of dimming under it.
        "hidden flex-none flex-col overflow-hidden px-2.5 py-3.5 lg:flex",
        "transition-[width] duration-[180ms]",
        collapsed ? "w-14" : "w-[210px]",
      )}
    >
      {backLink && (
        <div className="flex-none">
          <NavLink
            to={backLink.to}
            title={backLink.title ?? backLink.label}
            className={rowClass("mb-1.5 font-medium text-u-text2")}
          >
            <Icon d={backLink.icon} className="flex-none" />
            <span className={cn("min-w-0 truncate whitespace-nowrap", labelsHidden)}>{backLink.label}</span>
          </NavLink>
          <div className="mx-1 mb-1.5 h-px bg-u-border" />
        </div>
      )}

      <div className="-mx-2.5 min-h-0 flex-1 overflow-y-auto overflow-x-hidden px-2.5">
        {groups.map((group) => (
          <div key={group.label}>
            <div
              className={cn(
                "px-2.5 pb-1.5 pt-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3",
                labelsHidden,
              )}
            >
              {group.label}
            </div>
            {group.items.map((item) => (
              <SidebarRow key={item.to} item={item} collapsed={collapsed} />
            ))}
          </div>
        ))}
      </div>

      <div className="flex-none border-t border-u-border pt-3">
        <button
          type="button"
          onClick={toggle}
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          className={rowClass("text-u-text2")}
        >
          <Icon d={collapsed ? ICONS.expand : ICONS.collapse} className="flex-none" />
          <span className={cn("whitespace-nowrap", labelsHidden)}>Collapse</span>
        </button>
        <button
          type="button"
          onClick={() => openHelp("whatsNew")}
          title="What's new"
          className={cn(
            "px-2.5 pt-2 text-left font-mono text-[11px] text-u-text3 underline decoration-dotted underline-offset-2 hover:text-u-accent",
            labelsHidden,
          )}
        >
          {APP_VERSION} · What's new
        </button>
      </div>
    </nav>
  );
}

function SidebarRow({ item, collapsed }: { item: SidebarItem; collapsed: boolean }) {
  const labelsHidden = collapsed ? "hidden" : "";
  return (
    <NavLink
      to={item.to}
      end={item.end}
      title={item.leavesShell ?? item.label}
      className={({ isActive }) =>
        railRowClass(collapsed, isActive ? "bg-u-raised text-u-text [&_svg]:text-u-accent" : "text-u-text2")
      }
    >
      <Icon d={item.icon} className="flex-none" />
      <span className={cn("whitespace-nowrap", labelsHidden)}>{item.label}</span>
      {item.count !== undefined && (
        <span className={cn("ml-auto font-mono text-[11px] font-medium text-u-text3", labelsHidden)}>{item.count}</span>
      )}
      {item.leavesShell && <span className="sr-only">{item.leavesShell}</span>}
      {item.leavesShell && (
        <Icon d={ICONS.arrowRight} size={13} className={cn("ml-auto flex-none text-u-text3", labelsHidden)} />
      )}
    </NavLink>
  );
}

function railRowClass(collapsed: boolean, extra?: string) {
  return cn(
    "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13.5px] transition",
    "hover:bg-u-raised hover:text-u-text",
    collapsed && "justify-center",
    extra,
  );
}
