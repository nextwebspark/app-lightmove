import type { ProjectHealth, ProjectStage, ProjectType } from "../../features/projects/api/types";

/**
 * The mockups' stage pills and health dots, colour maps lifted from Workspace.dc.html's STAGES and
 * HEALTH tables.
 */
const STAGE_STYLES: Record<ProjectStage, { label: string; className: string }> = {
  BRIEF: { label: "Brief", className: "text-text2 border-line" },
  UNIVERSE: { label: "Universe", className: "text-sky bg-sky-dim border-transparent" },
  LOCKED: { label: "Universe locked", className: "text-sky border-sky" },
  MAPPING: { label: "Mapping", className: "text-amber bg-amber-dim border-transparent" },
  OUTREACH: { label: "Outreach live", className: "text-amber border-amber" },
  DELIVERED: { label: "Shortlist delivered", className: "text-green bg-green-dim border-transparent" },
  CLOSED: { label: "Closed", className: "text-text3 border-line-soft" },
};

export function stageLabel(stage: ProjectStage): string {
  return STAGE_STYLES[stage].label;
}

export function StagePill({ stage }: { stage: ProjectStage }) {
  const { label, className } = STAGE_STYLES[stage];
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border px-[9px] py-[3px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${className}`}
    >
      <span className="size-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}

/** What a mandate is engaged to deliver, in the pill shape the stages already use. */
const TYPE_STYLES: Record<ProjectType, { label: string; className: string }> = {
  MAPPING: { label: "Mapping only", className: "text-text2 border-line bg-panel2" },
  EXECUTIVE_SEARCH: { label: "Executive search", className: "text-sky bg-sky-dim border-transparent" },
};

export function ProjectTypeBadge({ projectType }: { projectType: ProjectType }) {
  const { label, className } = TYPE_STYLES[projectType];
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-md border px-[9px] py-[3px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${className}`}
    >
      {label}
    </span>
  );
}

const HEALTH_STYLES: Record<ProjectHealth, { label: string; dot: string; text: string; pill: string }> = {
  OK: { label: "On track", dot: "bg-green", text: "text-text2", pill: "text-green bg-green-dim" },
  RISK: { label: "At risk", dot: "bg-amber", text: "text-amber", pill: "text-amber bg-amber-dim" },
  OFF: { label: "Off track", dot: "bg-red", text: "text-red", pill: "text-red bg-red-dim" },
  DONE: { label: "Complete", dot: "bg-text3", text: "text-text3", pill: "text-text3 bg-panel2" },
};

export function HealthDot({ health }: { health: ProjectHealth }) {
  const { label, dot, text } = HEALTH_STYLES[health];
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap font-mono text-xs font-medium ${text}`}>
      <span className={`size-[7px] rounded-full ${dot}`} />
      {label}
    </span>
  );
}

/** The same four states as a filled pill, for the list's Status column and the drawer's header. */
export function HealthPill({ health }: { health: ProjectHealth }) {
  const { label, pill } = HEALTH_STYLES[health];
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-md px-[9px] py-[3px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${pill}`}
    >
      {label}
    </span>
  );
}
