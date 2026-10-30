import { findStatus, offeredStatusLabel, statusLabelOf } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";

const STAGE_PREFIX = "stage:";

/**
 * The Status column's header filter: the statuses the mapped executives actually hold, under their stage,
 * and the stage itself as one tick wherever it holds more than one of them.
 */
export function executiveStatusOptions(
  catalog: PipelineCatalog,
  seen: ReadonlySet<string>,
): { value: string; label: string; indent?: boolean }[] {
  const options: { value: string; label: string; indent?: boolean }[] = [];
  for (const stage of catalog.stages) {
    const held = stage.statuses.filter((status) => seen.has(status.key));
    if (held.length === 0) continue;
    const grouped = held.length > 1;
    if (grouped) options.push({ value: STAGE_PREFIX + stage.key, label: `All ${stage.label}` });
    for (const status of held) options.push({ value: status.key, label: offeredStatusLabel(stage, status), indent: grouped });
  }
  for (const key of seen) {
    if (!findStatus(catalog, key)) options.push({ value: key, label: statusLabelOf(key) });
  }
  return options;
}

/** The ticked options as the status keys the server filters on: a stage is every status under it. */
export function statusKeysOf(choice: readonly string[], catalog: PipelineCatalog): string[] {
  const keys = new Set<string>();
  for (const value of choice) {
    if (!value.startsWith(STAGE_PREFIX)) {
      keys.add(value);
      continue;
    }
    const stage = catalog.stages.find((candidate) => candidate.key === value.slice(STAGE_PREFIX.length));
    stage?.statuses.forEach((status) => keys.add(status.key));
  }
  return [...keys];
}
