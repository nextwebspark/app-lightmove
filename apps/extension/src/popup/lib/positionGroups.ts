import type { ProjectSummary } from "../../api/types";

export interface PositionGroup {
  label: string;
  positions: ProjectSummary[];
}

/** Recent first while nothing is typed, then every match grouped by client, A–Z. */
export function positionGroups(positions: ProjectSummary[], recentIds: string[], query: string): PositionGroup[] {
  const typed = query.trim().toLowerCase();
  const matches = positions.filter(
    (position) => !typed || `${position.positionTitle} ${position.clientName ?? ""}`.toLowerCase().includes(typed),
  );
  const groups: PositionGroup[] = [];
  if (!typed) {
    const recent = recentIds
      .map((id) => positions.find((position) => position.id === id))
      .filter((position): position is ProjectSummary => Boolean(position));
    if (recent.length) groups.push({ label: "Recent", positions: recent });
  }
  const byClient = new Map<string, ProjectSummary[]>();
  for (const position of matches) {
    const client = position.clientName ?? "No client";
    byClient.set(client, [...(byClient.get(client) ?? []), position]);
  }
  [...byClient.keys()]
    .sort((a, b) => a.localeCompare(b))
    .forEach((client) =>
      groups.push({
        label: client,
        positions: byClient.get(client)!.sort((a, b) => a.positionTitle.localeCompare(b.positionTitle)),
      }),
    );
  return groups;
}
