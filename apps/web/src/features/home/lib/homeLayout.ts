import { useCallback, useState } from "react";

/** The sections beside Needs you, in the order the person keeps them. */
export type HomeSection = "today" | "positions" | "charts";

/** The outreach groups Needs you can carry; the person's own tasks are always there. */
export type NeedsKind = "NEEDS_REPLY" | "FOLLOW_UP" | "PREPARATION" | "ATTENTION";

export type Landing = "home" | "positions";

export interface HomeLayout {
  sections: { key: HomeSection; on: boolean }[];
  kinds: Record<NeedsKind, boolean>;
  /** Workspace chart queries drawn as tiles, at most {@link MAX_PINNED_CHARTS}. */
  charts: string[];
  landing: Landing;
}

export const MAX_PINNED_CHARTS = 4;

export const DEFAULT_HOME_LAYOUT: HomeLayout = {
  sections: [
    { key: "today", on: true },
    { key: "positions", on: true },
    { key: "charts", on: true },
  ],
  kinds: { NEEDS_REPLY: true, FOLLOW_UP: true, PREPARATION: true, ATTENTION: true },
  charts: ["executivesEngaged", "positionsOffTrack", "openPositions"],
  landing: "home",
};

/** Whose layout, in which workspace: one browser may hold several people, and one person several workspaces. */
export interface HomeOwner {
  userId: string;
  workspaceId: string;
}

const storageKeyOf = ({ userId, workspaceId }: HomeOwner) => `lm-home:${userId}:${workspaceId}`;
const SECTIONS: readonly HomeSection[] = ["today", "positions", "charts"];

/**
 * A stored layout, or the default. Whatever the stored one is missing or got wrong is taken from the default, so a
 * section added later appears for everyone and nothing stored can break the page.
 */
export function readHomeLayout(owner: HomeOwner): HomeLayout {
  try {
    const raw = localStorage.getItem(storageKeyOf(owner));
    return raw ? normalised(JSON.parse(raw)) : DEFAULT_HOME_LAYOUT;
  } catch {
    return DEFAULT_HOME_LAYOUT;
  }
}

function normalised(stored: unknown): HomeLayout {
  if (!stored || typeof stored !== "object") return DEFAULT_HOME_LAYOUT;
  const value = stored as Partial<HomeLayout>;
  const kept = Array.isArray(value.sections)
    ? value.sections.filter(
        (section, index, all): section is { key: HomeSection; on: boolean } =>
          SECTIONS.includes(section?.key) &&
          typeof section.on === "boolean" &&
          all.findIndex((other) => other?.key === section.key) === index,
      )
    : [];
  const missing = DEFAULT_HOME_LAYOUT.sections.filter((section) => !kept.some((own) => own.key === section.key));
  const kinds = { ...DEFAULT_HOME_LAYOUT.kinds };
  for (const kind of Object.keys(kinds) as NeedsKind[]) {
    const own = value.kinds?.[kind];
    if (typeof own === "boolean") kinds[kind] = own;
  }
  const charts = Array.isArray(value.charts)
    ? value.charts.filter((id): id is string => typeof id === "string").slice(0, MAX_PINNED_CHARTS)
    : DEFAULT_HOME_LAYOUT.charts;
  return {
    sections: [...kept, ...missing],
    kinds,
    charts,
    landing: value.landing === "positions" ? "positions" : "home",
  };
}

export function isDefaultLayout(layout: HomeLayout): boolean {
  return JSON.stringify(layout) === JSON.stringify(DEFAULT_HOME_LAYOUT);
}

/** Where to land after signing in: Home, unless the person chose Positions in this workspace. */
export function landingPath(owner: HomeOwner): string {
  return readHomeLayout(owner).landing === "positions" ? "/positions" : "/";
}

/** The person's layout in this workspace and its setter; every change is kept for the next visit on this browser. */
export function useHomeLayout(owner: HomeOwner): [HomeLayout, (next: HomeLayout) => void] {
  const key = storageKeyOf(owner);
  const [kept, setKept] = useState(() => ({ key, layout: readHomeLayout(owner) }));
  // Another person or workspace in the same tab reads its own layout rather than keeping the last one's.
  const layout = kept.key === key ? kept.layout : readHomeLayout(owner);
  const save = useCallback(
    (next: HomeLayout) => {
      setKept({ key, layout: next });
      try {
        localStorage.setItem(key, JSON.stringify(next));
      } catch {
        // Blocked storage only costs the layout on the next visit.
      }
    },
    [key],
  );
  return [layout, save];
}

export function moveSection(layout: HomeLayout, key: HomeSection, by: -1 | 1): HomeLayout {
  const sections = [...layout.sections];
  const from = sections.findIndex((section) => section.key === key);
  const to = from + by;
  if (from < 0 || to < 0 || to >= sections.length) return layout;
  [sections[from], sections[to]] = [sections[to], sections[from]];
  return { ...layout, sections };
}
