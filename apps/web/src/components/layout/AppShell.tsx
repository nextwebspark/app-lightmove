import { useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { cn } from "../../lib/cn";
import { useAssistant } from "../../features/assistant/AssistantProvider";
import { AssistantDock } from "../../features/assistant/components/AssistantDock";
import { HelpProvider } from "../../features/help/HelpProvider";
import { MobileTabBar, type MobileTab } from "./mobile/MobileTabBar";
import { Sidebar, type SidebarGroup, type SidebarItem } from "./Sidebar";
import { Topbar } from "./Topbar";

/**
 * The chrome every signed-in screen shares. Below `lg` the rail becomes an overlay drawer this
 * component opens and closes — the state lives here because closing it on navigation is the shell's
 * job, not the rail's.
 */
export function AppShell({
  navGroups,
  navBackLink,
  breadcrumb,
  topbarActions,
  notice,
  contentClassName,
  assistantContext = "Workspace",
  assistantProjectId = null,
  mobileTabs,
  onQuickAction,
  children,
}: {
  navGroups: SidebarGroup[];
  navBackLink?: SidebarItem;
  breadcrumb?: ReactNode;
  topbarActions?: ReactNode;
  /**
   * A line about the whole screen — an archived position's — between the topbar and the page. Outside `main`,
   * whose height the viewport-filling tabs size their own scrolling against.
   */
  notice?: ReactNode;
  contentClassName?: string;
  /** Which mandate the assistant is asking about here. A workspace screen has none. */
  assistantContext?: string;
  assistantProjectId?: string | null;
  /** The phone's bottom bar below `lg`; without it the topbar's menu button opens the rail, as before. */
  mobileTabs?: readonly MobileTab[];
  /** The bottom bar's centre +. */
  onQuickAction?: () => void;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const { isOpenFor } = useAssistant();

  useEffect(() => setNavOpen(false), [pathname]);

  useEffect(() => {
    if (!navOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setNavOpen(false);
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [navOpen]);

  return (
    <HelpProvider>
      <div className="flex h-dvh flex-col overflow-hidden">
        <Topbar
          breadcrumb={breadcrumb}
          actions={topbarActions}
          navOpen={navOpen}
          onMenuClick={() => setNavOpen(true)}
          menuInBottomBar={!!mobileTabs}
        />

        {notice && <div className="px-3.5 pb-2.5">{notice}</div>}

        <div
          className={cn(
            "flex min-h-0 flex-1 px-3.5 pb-3.5",
            // The bottom bar's own height, so main ends above it rather than under it.
            mobileTabs && "max-lg:pb-[calc(4rem+env(safe-area-inset-bottom))]",
          )}
        >
          {navOpen && (
            <div
              className={cn(
                "fixed inset-0 z-[90] bg-u-scrim lg:hidden",
                mobileTabs && "bottom-[calc(3.5rem+env(safe-area-inset-bottom))]",
              )}
              onClick={() => setNavOpen(false)}
            />
          )}

          <Sidebar
            groups={navGroups}
            backLink={navBackLink}
            open={navOpen}
            onClose={() => setNavOpen(false)}
            aboveBottomBar={!!mobileTabs}
            assistantOpen={
              !!assistantProjectId && isOpenFor(assistantProjectId)
            }
          />

          <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-u-border-strong bg-u-surface">
            <div className={cn(contentClassName)}>{children}</div>
          </main>

          {/* Docked, not overlaid: main is flex-1, so this narrows it and covers nothing. The mockup
            draws it this way because the grid has to stay tickable while the assistant is open. The
            dock is the slot rather than the panel, so main narrows on the same curve the panel
            arrives on instead of losing its width a frame ahead of it. */}
          {/* Inside a project only, and opened from Strategy's AI Research: no screen floats its own way in. */}
          {assistantProjectId && (
            <AssistantDock
              contextLabel={assistantContext}
              projectId={assistantProjectId}
            />
          )}
        </div>

        {mobileTabs && (
          <MobileTabBar
            tabs={mobileTabs}
            pathname={pathname}
            moreOpen={navOpen}
            onMore={() => setNavOpen((open) => !open)}
            onQuickAction={onQuickAction}
          />
        )}
      </div>
    </HelpProvider>
  );
}
