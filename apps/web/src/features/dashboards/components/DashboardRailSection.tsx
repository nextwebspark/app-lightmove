import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { Dashboard } from "../api/types";

export const DASHBOARD_PARAM = "dashboard";

/** "Your dashboards" under the report's chapters: the member's own and shared ones, and New dashboard. */
export function DashboardRailSection({
  dashboards,
  activeId,
  collapsed,
  onNew,
}: {
  dashboards: Dashboard[];
  activeId: string | null;
  collapsed: boolean;
  onNew: () => void;
}) {
  return (
    <div className="mt-3 border-t border-u-border pt-3 lg:mt-5 lg:pt-4">
      <div className={cn("type-micro-label mx-2.5 mb-2", collapsed && "lg:hidden")}>Your dashboards</div>
      <nav aria-label="Your dashboards" className="flex gap-0.5 overflow-x-auto lg:flex-col lg:overflow-visible">
        {dashboards.map((dashboard) => {
          const isActive = dashboard.id === activeId;
          return (
            <Link
              key={dashboard.id}
              to={{ search: `?${DASHBOARD_PARAM}=${dashboard.id}` }}
              aria-current={isActive ? "page" : undefined}
              aria-label={collapsed ? dashboard.name : undefined}
              title={collapsed ? dashboard.name : undefined}
              className={cn(
                "flex flex-none items-center gap-2.5 whitespace-nowrap rounded-u-control px-2.5 py-2 text-body transition",
                isActive
                  ? "bg-u-sunken font-medium text-u-text [&_svg]:text-u-accent"
                  : "text-u-text2 hover:bg-u-raised hover:text-u-text",
                collapsed && "lg:justify-center",
              )}
            >
              <Icon d={ICONS.dashboard} className="flex-none" />
              <span className={cn("min-w-0 flex-1 truncate", collapsed && "lg:hidden")}>{dashboard.name}</span>
              {dashboard.shared && (
                <span title={dashboard.owned ? "Shared with the team" : "Shared by a teammate"}
                      className={cn("flex-none text-u-text3", collapsed && "lg:hidden")}>
                  <Icon d={ICONS.team} size={13} />
                </span>
              )}
            </Link>
          );
        })}
        <button
          type="button"
          onClick={onNew}
          aria-label="New dashboard"
          title={collapsed ? "New dashboard" : undefined}
          className={cn(
            "flex flex-none items-center gap-2.5 whitespace-nowrap rounded-u-control px-2.5 py-2 text-body text-u-accent transition hover:bg-u-raised",
            collapsed && "lg:justify-center",
          )}
        >
          <Icon d={ICONS.plus} className="flex-none" />
          <span className={cn(collapsed && "lg:hidden")}>New dashboard</span>
        </button>
      </nav>
    </div>
  );
}
