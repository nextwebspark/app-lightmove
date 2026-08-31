import { useId } from "react";
import type { ProjectSummary } from "../../api/types";

interface ProjectSelectProps {
  projects: ProjectSummary[];
  selectedProjectId: string | null;
  onSelect: (projectId: string) => void;
  isLoading: boolean;
  /** Offered as the empty choice where "no mandate" is a real answer, as it is in Settings. */
  unsetLabel?: string;
}

/**
 * Which mandate the company goes to.
 *
 * The full-width select the design puts above the two destination buttons. It lists exactly what
 * `GET /projects` returned — the API already scopes that to the caller's workspace and their seats,
 * so the popup neither filters nor adds to it.
 */
export function ProjectSelect({
  projects,
  selectedProjectId,
  onSelect,
  isLoading,
  unsetLabel,
}: ProjectSelectProps) {
  const selectId = useId();
  return (
    <div>
      <label
        htmlFor={selectId}
        className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.11em] text-text3"
      >
        Project
      </label>
      <select
        id={selectId}
        value={selectedProjectId ?? ""}
        disabled={isLoading || (projects.length === 0 && !unsetLabel)}
        onChange={(event) => onSelect(event.target.value)}
        className="mt-1 w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-mono text-[12px] text-text outline-none focus:border-sky disabled:text-text3"
      >
        {unsetLabel && <option value="">{unsetLabel}</option>}
        {isLoading && <option value="">Loading mandates…</option>}
        {!isLoading && projects.length === 0 && <option value="">No mandates available</option>}
        {projects.map((project) => (
          <option key={project.id} value={project.id}>
            {project.clientName ? `${project.positionTitle} — ${project.clientName}` : project.positionTitle}
          </option>
        ))}
      </select>
    </div>
  );
}
