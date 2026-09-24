import { useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { cn } from "../../lib/cn";
import { useAssistant } from "../../features/assistant/AssistantProvider";
import { AssistantDock } from "../../features/assistant/components/AssistantDock";
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
  contentClassName,
  assistantContext = "Workspace",
  assistantProjectId = null,
  children,
}: {
  navGroups: SidebarGroup[];
  navBackLink?: SidebarItem;
  breadcrumb?: ReactNode;
  contentClassName?: string;
  /** Which mandate the assistant is asking about here. A workspace screen has none. */
  assistantContext?: string;
  assistantProjectId?: string | null;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const { open: assistantOpen } = useAssistant();

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
    <div className="flex h-dvh flex-col overflow-hidden">
      <Topbar breadcrumb={breadcrumb} navOpen={navOpen} onMenuClick={() => setNavOpen(true)} />

      <div className="flex min-h-0 flex-1 px-3.5 pb-3.5">
        {navOpen && (
          <div
            className="fixed inset-0 z-[90] bg-u-scrim lg:hidden"
            onClick={() => setNavOpen(false)}
          />
        )}

        <Sidebar
          groups={navGroups}
          backLink={navBackLink}
          open={navOpen}
          onClose={() => setNavOpen(false)}
          assistantOpen={!!assistantProjectId && assistantOpen}
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
          <AssistantDock contextLabel={assistantContext} projectId={assistantProjectId} />
        )}
      </div>
    </div>
  );
}
