import { useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { cn } from "../../lib/cn";
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
  navHeader,
  breadcrumb,
  contentClassName,
  children,
}: {
  navGroups: SidebarGroup[];
  navBackLink?: SidebarItem;
  navHeader?: ReactNode;
  breadcrumb?: ReactNode;
  contentClassName?: string;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const [navOpen, setNavOpen] = useState(false);

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
            className="fixed inset-0 z-[90] bg-[rgba(15,20,30,0.4)] lg:hidden"
            onClick={() => setNavOpen(false)}
          />
        )}

        <Sidebar
          groups={navGroups}
          backLink={navBackLink}
          header={navHeader}
          open={navOpen}
          onClose={() => setNavOpen(false)}
        />

        <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-line bg-panel">
          <div className={cn(contentClassName)}>{children}</div>
        </main>
      </div>
    </div>
  );
}
