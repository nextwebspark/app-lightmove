import { useId, useState } from "react";
import { Link, matchPath, NavLink, useLocation, type To } from "react-router-dom";
import { cn } from "../../lib/cn";
import { useHelp } from "../../features/help/HelpProvider";
import { APP_VERSION } from "../../lib/version";
import { Icon, ICONS } from "./Icon";
import { hasActiveView } from "./sidebarViews";
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
  /** The section's own views, nested under it: open while the section is, or by its chevron. */
  children?: SidebarSubItem[];
}

/** One view of a section. `active` is the caller's, since a view is often a query parameter `NavLink` cannot see. */
export interface SidebarSubItem {
  key: string;
  to: To;
  /** Navigation state the link carries, such as a page's "the user chose this view" flag. */
  state?: unknown;
  label: string;
  icon: string;
  active: boolean;
}

export interface SidebarGroup {
  label: string;
  items: SidebarItem[];
}

/**
 * The mockups' left rail: grouped nav links with the collapse row pinned to the bottom, 210px
 * wide or 56px collapsed (labels, group headers and counts disappear). On desktop it is a bare column
 * on the page ground, not a card — the main panel is the only card the mockups draw.
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
        "hidden flex-none flex-col overflow-y-auto overflow-x-hidden px-2.5 py-3.5 lg:flex",
        "transition-[width] duration-[180ms]",
        collapsed ? "w-14" : "w-[210px]",
      )}
    >
      {backLink && (
        <>
          <NavLink
            to={backLink.to}
            title={backLink.title ?? backLink.label}
            className={rowClass("mb-1.5 font-medium text-u-text2")}
          >
            <Icon d={backLink.icon} className="flex-none" />
            <span className={cn("min-w-0 truncate whitespace-nowrap", labelsHidden)}>{backLink.label}</span>
          </NavLink>
          <div className="mx-1 mb-1.5 h-px bg-u-border" />
        </>
      )}

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

      <div className="mt-auto border-t border-u-border pt-3">
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
  const { pathname } = useLocation();
  const viewsId = useId();
  const views = item.children ?? [];
  const viewActive = hasActiveView(item);
  const inSection =
    views.length > 0 && (viewActive || matchPath({ path: item.to, end: item.end ?? false }, pathname) !== null);
  // A chevron press holds until the section is entered or left, which opens or closes the views again. The
  // collapsed rail has no chevron, so it always shows the section's own.
  const [pressed, setPressed] = useState<boolean | null>(null);
  const [pressedInSection, setPressedInSection] = useState(inSection);
  if (pressedInSection !== inSection) {
    setPressedInSection(inSection);
    setPressed(null);
  }
  const open = views.length > 0 && (collapsed ? inSection : (pressed ?? inSection));
  // A section shut by hand still says where you are: its own row stands in for the view inside it.
  const viewShown = viewActive && open;
  const labelsHidden = collapsed ? "hidden" : "";

  const link = (
    <NavLink
      to={item.to}
      end={item.end}
      title={item.leavesShell ?? item.label}
      // NavLink applies it only while active, and an undefined one falls back to its own "page".
      aria-current={viewShown ? "false" : "page"}
      className={({ isActive }) =>
        railRowClass(
          collapsed,
          isActive && !viewShown
            ? "bg-u-raised text-u-text [&_svg]:text-u-accent"
            : viewActive
              ? "text-u-text"
              : "text-u-text2",
        )
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
  if (views.length === 0) return link;

  return (
    <div>
      <div className="flex items-center gap-0.5">
        <div className="min-w-0 flex-1">{link}</div>
        {!collapsed && (
          <button
            type="button"
            aria-expanded={open}
            aria-controls={viewsId}
            aria-label={`${item.label} views`}
            title={`${open ? "Hide" : "Show"} ${item.label} views`}
            onClick={() => setPressed(!open)}
            className="grid size-6 flex-none place-items-center rounded-md text-u-text3 transition hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.chevronDown} size={13} className={cn("transition-transform", !open && "-rotate-90")} />
          </button>
        )}
      </div>
      <SidebarViews id={viewsId} label={item.label} views={views} open={open} collapsed={collapsed} />
    </div>
  );
}

function SidebarViews({
  id,
  label,
  views,
  open,
  collapsed,
}: {
  id: string;
  label: string;
  views: SidebarSubItem[];
  open: boolean;
  collapsed: boolean;
}) {
  return (
    <div
      id={id}
      role="group"
      aria-label={label}
      hidden={!open}
      className={cn(
        "mt-px flex flex-col gap-px",
        collapsed ? "mb-1.5 rounded-lg border border-u-border p-0.5" : "mb-1 ml-[18px] border-l border-u-border pl-2",
      )}
    >
      {views.map((view) => (
        <Link
          key={view.key}
          to={view.to}
          state={view.state}
          title={view.label}
          aria-current={view.active ? "page" : undefined}
          className={railRowClass(
            collapsed,
            cn("gap-2 py-1.5 text-body", view.active ? "bg-u-raised text-u-text [&_svg]:text-u-accent" : "text-u-text2"),
          )}
        >
          <Icon d={view.icon} size={14} className="flex-none" />
          <span className={cn("whitespace-nowrap", collapsed && "hidden")}>{view.label}</span>
        </Link>
      ))}
    </div>
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
