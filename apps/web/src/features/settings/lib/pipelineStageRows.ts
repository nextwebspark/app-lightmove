import type { PipelineRetirePreview } from "../../workspace/api/types";

export const PIPELINE_NAME_LIMIT = 40;
const PREVIEWED_STATUSES = 3;

export function statusCountLabel(count: number): string {
  return count === 1 ? "1 status" : `${count} statuses`;
}

/** What a folded stage says in place of its statuses: the first few by name, and how many more. */
export function statusPreview(labels: readonly string[]): string {
  if (labels.length === 0) return "No statuses yet";
  const shown = labels.slice(0, PREVIEWED_STATUSES).join(", ");
  return labels.length > PREVIEWED_STATUSES ? `${shown} +${labels.length - PREVIEWED_STATUSES}` : shown;
}

/** `rows` in the order `ids` names them; a row `ids` leaves out goes last, keeping its place among such rows. */
export function inOrderOf<T extends { id: string }>(rows: readonly T[], ids: readonly string[]): T[] {
  const rank = (row: T) => {
    const index = ids.indexOf(row.id);
    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
  };
  return [...rows].sort((a, b) => rank(a) - rank(b));
}

/** Whether a retire needs asking about at all: someone holds a status, or an automation would stop. */
export function retireTouchesAnyone(preview: PipelineRetirePreview): boolean {
  return preview.automations.length > 0 || preview.statuses.some((status) => status.holders > 0);
}
