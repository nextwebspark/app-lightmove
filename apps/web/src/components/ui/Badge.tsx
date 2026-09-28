import type { ProjectHealth, ProjectStage } from "../../features/projects/api/types";
import { Icon, ICONS } from "../layout/Icon";

/**
 * The mockups' stage pills, colour map lifted from Workspace.dc.html's STAGES table, and the health
 * indicators.
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

type HealthGlyph = "trendingUp" | "warning" | "trendingDown" | "checkCircle";

/** Icon-led health indicators (Uncava status spec), so the state never rests on colour alone. */
const HEALTH_STYLES: Record<ProjectHealth, { label: string; icon: HealthGlyph; tone: string; tint: string }> = {
  OK: { label: "On track", icon: "trendingUp", tone: "text-u-direct", tint: "bg-u-direct-tint" },
  RISK: { label: "At risk", icon: "warning", tone: "text-u-signal", tint: "bg-u-signal-tint" },
  OFF: { label: "Off track", icon: "trendingDown", tone: "text-u-offlimits", tint: "bg-u-offlimits-tint" },
  DONE: { label: "Complete", icon: "checkCircle", tone: "text-u-text3", tint: "bg-u-raised" },
};

/** Badge variant — headers, overview cards and drawers. */
export function HealthPill({ health }: { health: ProjectHealth }) {
  const { label, icon, tone, tint } = HEALTH_STYLES[health];
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-[9px] py-[3px] text-[11px] font-semibold ${tone} ${tint}`}
    >
      <Icon d={ICONS[icon]} size={13} className="shrink-0" />
      {label}
    </span>
  );
}

/** Inline variant — dense tables and list rows: a coloured glyph beside a neutral label. */
export function HealthInline({ health }: { health: ProjectHealth }) {
  const { label, icon, tone } = HEALTH_STYLES[health];
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-xs font-medium text-u-text">
      <Icon d={ICONS[icon]} size={14} className={`shrink-0 ${tone}`} />
      {label}
    </span>
  );
}

/** Icon-only variant — ultra-compact grids; the label rides on the tooltip and the accessible name. */
export function HealthIcon({ health }: { health: ProjectHealth }) {
  const { label, icon, tone, tint } = HEALTH_STYLES[health];
  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      className={`inline-flex size-7 shrink-0 items-center justify-center rounded-full ${tone} ${tint}`}
    >
      <Icon d={ICONS[icon]} size={14} />
    </span>
  );
}
