import type { ProjectHealth, ProjectStage } from "../../features/projects/api/types";

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

const HEALTH_STYLES: Record<ProjectHealth, { label: string; dot: string; text: string }> = {
  OK: { label: "On track", dot: "bg-green", text: "text-text2" },
  RISK: { label: "At risk", dot: "bg-amber", text: "text-amber" },
  OFF: { label: "Off track", dot: "bg-red", text: "text-red" },
  DONE: { label: "Complete", dot: "bg-text3", text: "text-text3" },
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

export function LeadBadge() {
  return (
    <span className="rounded-[5px] bg-amber-dim px-[7px] py-0.5 font-mono text-[10px] font-semibold tracking-[0.08em] text-amber">
      LEAD
    </span>
  );
}
