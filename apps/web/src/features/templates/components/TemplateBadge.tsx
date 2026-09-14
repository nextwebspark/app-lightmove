import { cn } from "../../../lib/cn";
import type { TemplateOrigin, TemplateOverview, TemplateScope } from "../api/types";

const PILL = "inline-flex rounded-full border border-line-soft px-2 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em]";

const ORIGIN_BADGES: Record<TemplateOrigin, { label: string; className: string }> = {
  LIBRARY: { label: "Library", className: "bg-panel text-text2" },
  CUSTOMISED: { label: "Customised", className: "bg-amber-dim text-amber" },
  OWN: { label: "Your own", className: "bg-sky-dim text-sky" },
  HIDDEN: { label: "Hidden", className: "bg-panel text-text3" },
};

/** Where a template comes from (a firm's list) or whether it is live (the library). */
export function TemplateBadge({ scope, template }: { scope: TemplateScope; template: TemplateOverview }) {
  const { label, className } = badgeOf(scope, template);
  return <span className={cn(PILL, className)}>{label}</span>;
}

export function LibraryUpdatedPill() {
  return (
    <span
      title="The library version changed after your firm customised this template"
      className={cn(PILL, "items-center gap-1.5 border-transparent bg-amber-dim text-amber")}
    >
      <span className="size-1.5 rounded-full bg-amber-btn" />
      Library updated
    </span>
  );
}

function badgeOf(scope: TemplateScope, template: TemplateOverview) {
  if (scope === "workspace") return ORIGIN_BADGES[template.origin ?? "LIBRARY"];
  if (template.fallback) return { label: "Fallback", className: "bg-sky-dim text-sky" };
  return template.active
    ? { label: "Active", className: "bg-green-dim text-green" }
    : { label: "Archived", className: "bg-panel text-text3" };
}
