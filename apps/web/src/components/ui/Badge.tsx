import type { ProjectHealth, ProjectStage } from "../../features/projects/api/types";

/**
 * The mockups' stage pills and health dots, colour maps lifted from Workspace.dc.html's STAGES and
 * HEALTH tables.
 */
const STAGE_STYLES: Record<ProjectStage, { label: string; className: string }> = {
  BRIEF: { label: "Brief", className: "text-u-text2 border-u-border-strong" },
  UNIVERSE: { label: "Universe", className: "text-u-accent bg-u-accent-tint border-transparent" },
  LOCKED: { label: "Universe locked", className: "text-u-accent border-u-accent" },
  MAPPING: { label: "Mapping", className: "text-u-accent bg-u-accent-tint border-transparent" },
  OUTREACH: { label: "Outreach live", className: "text-u-accent border-u-accent" },
  DELIVERED: { label: "Shortlist delivered", className: "text-u-direct bg-u-direct-tint border-transparent" },
  CLOSED: { label: "Closed", className: "text-u-text3 border-u-border" },
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
  OK: { label: "On track", dot: "bg-u-direct", text: "text-u-text2" },
  RISK: { label: "At risk", dot: "bg-u-signal", text: "text-u-signal" },
  OFF: { label: "Off track", dot: "bg-u-offlimits", text: "text-u-offlimits" },
  DONE: { label: "Complete", dot: "bg-u-text3", text: "text-u-text3" },
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
