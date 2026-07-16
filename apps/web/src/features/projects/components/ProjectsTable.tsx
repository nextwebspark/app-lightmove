import { Avatar, HealthDot, StagePill } from "../../../components/ui";
import { formatDate } from "../../../lib/format";
import type { Project, TeamMember } from "../api/types";
import type { SortKey } from "../lib/filtering";

/** The mandate list: sortable mono headers, stage pills, health dots, overlapping avatar stacks. */
export function ProjectsTable({
  projects,
  sortKey,
  sortDirection,
  onSort,
  onOpen,
}: {
  projects: Project[];
  sortKey: SortKey;
  sortDirection: 1 | -1;
  onSort: (key: SortKey) => void;
  onOpen: (projectId: string) => void;
}) {
  const arrow = (key: SortKey) => (sortKey === key ? (sortDirection === 1 ? "↑" : "↓") : "");

  const th =
    "whitespace-nowrap border-b border-line px-3 py-[9px] text-left font-mono text-[10.5px] " +
    "font-semibold uppercase tracking-[0.12em] text-text3";
  const td = "border-b border-line-soft px-3 py-[11px]";

  return (
    <table className="w-full border-collapse">
      <thead>
        <tr>
          <th className={`${th} cursor-pointer`} onClick={() => onSort("client")}>
            Client <span className="text-amber">{arrow("client")}</span>
          </th>
          <th className={th}>Position</th>
          <th className={`${th} cursor-pointer`} onClick={() => onSort("stage")}>
            Stage <span className="text-amber">{arrow("stage")}</span>
          </th>
          <th className={th}>Health</th>
          <th className={th}>Team</th>
          <th className={`${th} cursor-pointer`} onClick={() => onSort("date")}>
            Target <span className="text-amber">{arrow("date")}</span>
          </th>
          <th className={th}>Pipeline</th>
        </tr>
      </thead>
      <tbody>
        {projects.map((project) => (
          <tr key={project.id} className="cursor-pointer hover:bg-panel2" onClick={() => onOpen(project.id)}>
            <td className={`${td} whitespace-nowrap font-mono text-[12.5px] font-medium text-text2`}>
              {project.clientName}
            </td>
            <td className={`${td} whitespace-nowrap`}>
              <span className="text-[13px] font-semibold text-text">{project.positionTitle}</span>
              <span className="mt-0.5 block font-mono text-[11px] text-text3">
                Lead · {leadOf(project.team)?.fullName ?? "—"}
              </span>
            </td>
            <td className={td}>
              <StagePill stage={project.stage} />
            </td>
            <td className={td}>
              <HealthDot health={project.health} />
            </td>
            <td className={td}>
              <span className="flex">
                {project.team.map((seat, index) => (
                  <Avatar
                    key={seat.memberId}
                    id={seat.memberId}
                    name={seat.fullName}
                    size="sm"
                    className={`border-2 border-panel ${index > 0 ? "-ml-[7px]" : ""}`}
                  />
                ))}
              </span>
            </td>
            <td className={`${td} whitespace-nowrap font-mono text-xs text-text2`}>
              {formatDate(project.targetDate)}
            </td>
            <td className={`${td} whitespace-nowrap font-mono text-xs text-text2`}>
              <b className="font-semibold text-text">{project.companies}</b> cos ·{" "}
              <b className="font-semibold text-text">{project.candidates}</b> cand
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function leadOf(team: TeamMember[]): TeamMember | undefined {
  // Leads are plural now, and a fresh project may have none beyond its admin — whoever runs it.
  return (
    team.find((seat) => seat.projectRoles.includes("LEAD")) ??
    team.find((seat) => seat.projectRoles.includes("ADMIN"))
  );
}
