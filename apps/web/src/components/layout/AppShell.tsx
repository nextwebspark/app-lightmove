import { useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { cn } from "../../lib/cn";
import { useAssistant } from "../../features/assistant/AssistantProvider";
import { AssistantDock } from "../../features/assistant/components/AssistantDock";
import { HelpProvider } from "../../features/help/HelpProvider";
import { MobileMoreSheet } from "./mobile/MobileMoreSheet";
import { MobileTabBar, type MobileTab } from "./mobile/MobileTabBar";
import { Sidebar, type SidebarGroup, type SidebarItem } from "./Sidebar";
import { Topbar } from "./Topbar";

/**
 * The chrome every signed-in screen shares: the rail from `lg`, and below it the bottom bar whose More opens the
 * same screens in a panel — the state lives here because closing it on navigation is the shell's job.
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
  mobileTitle,
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
  /** The phone's bottom bar below `lg`. */
  mobileTabs: readonly MobileTab[];
  /** The phone's More panel's heading: the position, the workspace, or Settings. */
  mobileTitle: string;
  /** The bottom bar's centre +. */
  onQuickAction?: () => void;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  const { isOpenFor } = useAssistant();

  useEffect(() => setMoreOpen(false), [pathname]);

  return (
    <HelpProvider>
      {/* Clear of the notch and the status bar: the home-screen app is drawn edge to edge (viewport-fit=cover). */}
      <div className="flex h-dvh flex-col overflow-hidden pt-[env(safe-area-inset-top)] ps-[env(safe-area-inset-left)] pe-[env(safe-area-inset-right)]">
        <Topbar breadcrumb={breadcrumb} actions={topbarActions} />

        {notice && <div className="px-3.5 pb-2.5">{notice}</div>}

        {/* Below `lg` main ends above the bottom bar rather than under it. */}
        <div className="flex min-h-0 flex-1 px-3.5 pb-3.5 max-lg:pb-[calc(var(--mobile-nav-clearance)+0.875rem)]">
          <Sidebar
            groups={navGroups}
            backLink={navBackLink}
            assistantOpen={!!assistantProjectId && isOpenFor(assistantProjectId)}
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

        <MobileTabBar
          tabs={mobileTabs}
          pathname={pathname}
          moreOpen={moreOpen}
          onMore={() => setMoreOpen((open) => !open)}
          onQuickAction={onQuickAction}
        />
        <MobileMoreSheet
          open={moreOpen}
          onClose={() => setMoreOpen(false)}
          title={mobileTitle}
          groups={navGroups}
          backLink={navBackLink}
        />
      </div>
    </HelpProvider>
  );
}
