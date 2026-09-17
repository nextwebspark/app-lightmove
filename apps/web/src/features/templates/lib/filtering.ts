import type { TemplateOrigin, TemplateOverview, TemplateScope } from "../api/types";
import { DISCIPLINE_LABELS } from "./labels";

const WORKSPACE_STATUS_ORDER: TemplateOrigin[] = ["OWN", "CUSTOMISED", "LIBRARY", "HIDDEN"];

export function filterTemplates(templates: TemplateOverview[], query: string): TemplateOverview[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return templates;
  return templates.filter((template) => searchTextOf(template).includes(needle));
}

/** The order the Status column sorts in: what the firm made first, what it hid last. */
export function statusRank(scope: TemplateScope, template: TemplateOverview): number {
  if (scope === "library") return template.fallback ? 0 : template.active ? 1 : 2;
  return WORKSPACE_STATUS_ORDER.indexOf(template.origin ?? "LIBRARY");
}

export function toggleLabelOf(scope: TemplateScope, template: TemplateOverview): string | null {
  if (template.fallback) return null;
  if (scope === "library") return template.active ? "Archive" : "Restore";
  if (template.origin === "LIBRARY") return "Hide";
  if (template.origin === "HIDDEN") return "Show";
  return null;
}

/** A library template seen from a firm carries no reviser's name: the author is LightMove, not a colleague. */
export function reviserOf(scope: TemplateScope, template: TemplateOverview): string | null {
  if (template.revisedByName) return template.revisedByName;
  const isLibraryRow = template.origin === "LIBRARY" || template.origin === "HIDDEN";
  return scope === "workspace" && isLibraryRow ? "LightMove" : null;
}

function searchTextOf(template: TemplateOverview): string {
  return [
    template.title,
    template.code,
    template.summary ?? "",
    DISCIPLINE_LABELS[template.discipline],
    ...template.keywords,
  ]
    .join(" ")
    .toLowerCase();
}
