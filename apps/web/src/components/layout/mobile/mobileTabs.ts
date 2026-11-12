import { ICONS } from "../Icon";
import type { MobileTab } from "./MobileTabBar";

/** The workspace's bottom bar — the lists, Settings included. A pure client has their positions alone. */
export function workspaceTabs(clientOnly: boolean, listSearch = ""): MobileTab[] {
  if (clientOnly) return [{ to: "/", label: "Positions", icon: ICONS.myProjects, end: true }];
  return [
    { to: `/${listSearch}`, label: "Positions", icon: ICONS.myProjects, end: true },
    { to: "/tasks", label: "Tasks", icon: ICONS.checkCircle },
    { to: "/candidates", label: "Candidates", icon: ICONS.candidates },
  ];
}

/**
 * A position's bottom bar. A researcher's day is the brief, the companies — the pipeline is one of their views — and
 * the conversations; a client seat reads the brief, the map and the report.
 */
export function positionTabs(projectId: string, isStaff: boolean): MobileTab[] {
  const base = `/projects/${projectId}`;
  const companies: MobileTab = {
    to: `${base}/companies/universe`,
    label: "Companies",
    icon: ICONS.globe,
    activeWhen: (path) => path.startsWith(`${base}/companies/`),
  };
  return isStaff
    ? [
        { to: base, label: "Brief", icon: ICONS.position, end: true },
        companies,
        { to: `${base}/outreach`, label: "Outreach", icon: ICONS.outreach },
      ]
    : [
        { to: base, label: "Brief", icon: ICONS.position, end: true },
        companies,
        { to: `${base}/reports`, label: "Dashboard", icon: ICONS.dashboard },
      ];
}
