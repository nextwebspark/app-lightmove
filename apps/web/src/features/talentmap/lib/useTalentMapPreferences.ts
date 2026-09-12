import { useCallback, useEffect, useState } from "react";

/** How the Companies screen is read: the grid, or the globe. */
export type CompaniesView = "table" | "map";

export interface TalentMapPreferences {
  view: CompaniesView;
  panelCollapsed: boolean;
  showExecutives: boolean;
}

const DEFAULTS: TalentMapPreferences = { view: "table", panelCollapsed: false, showExecutives: true };

const storageKey = (projectId: string) => `lm.companies.map.${projectId}`;

/**
 * The map view's three working habits, remembered per mandate in `localStorage` beside the grid's
 * sort and layout. Validated on read, for the reason `useGridSort` validates: a value written by an
 * older build must not become a broken screen nobody can reset.
 */
export function useTalentMapPreferences(projectId: string) {
  // The mandate is held beside the preferences rather than read once on mount: a caller that keeps
  // this hook across a project switch would otherwise write the old mandate's habits under the new
  // mandate's key, overwriting what that project had saved.
  const [held, setHeld] = useState(() => ({ projectId, preferences: read(projectId) }));
  const preferences = held.projectId === projectId ? held.preferences : read(projectId);
  if (held.projectId !== projectId) {
    setHeld({ projectId, preferences });
  }

  useEffect(() => {
    try {
      localStorage.setItem(storageKey(projectId), JSON.stringify(preferences));
    } catch {
      // A blocked store costs a remembered view, not the screen.
    }
  }, [projectId, preferences]);

  const update = useCallback((changes: Partial<TalentMapPreferences>) => {
    setHeld((current) => ({
      projectId: current.projectId,
      preferences: { ...current.preferences, ...changes },
    }));
  }, []);

  return [preferences, update] as const;
}

function read(projectId: string): TalentMapPreferences {
  try {
    const raw = localStorage.getItem(storageKey(projectId));
    if (!raw) return DEFAULTS;
    const parsed = JSON.parse(raw) as Partial<TalentMapPreferences>;
    return {
      view: parsed.view === "map" ? "map" : "table",
      panelCollapsed: parsed.panelCollapsed === true,
      showExecutives: parsed.showExecutives !== false,
    };
  } catch {
    return DEFAULTS;
  }
}
