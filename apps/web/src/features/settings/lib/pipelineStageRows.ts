import type { PipelineStage, PipelineStatus } from "../../workspace/api/types";

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

/** The stages a status may move to: any other live one, but a built-in status never crosses into or out of the closed stage. */
export function moveTargetsOf(status: PipelineStatus, from: PipelineStage, stages: readonly PipelineStage[]): PipelineStage[] {
  return stages.filter(
    (stage) => !stage.retired && stage.id !== from.id && (!status.system || stage.kind === from.kind),
  );
}
