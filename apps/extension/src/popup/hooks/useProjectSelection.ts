import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { ProjectSummary } from "../../api/types";
import { useCaptureSettings } from "./useCaptureSettings";

/**
 * The position a capture lands in — the panel's context, chosen at the top and kept until changed. Defaults to the one
 * chosen in Settings, else the last one used, else the first. Archived positions are never offered.
 */
const LAST_USED_KEY = ["extension", "lastUsedProject"] as const;
const RECENT_KEY = ["extension", "recentProjects"] as const;

export function useProjectSelection() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const { settings, hasReadSettings } = useCaptureSettings();

  const projects = useQuery<ProjectSummary[]>({
    queryKey: ["extension", "projects"],
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "listProjects" });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
    },
  });

  const lastUsed = useQuery<string | null>({
    queryKey: LAST_USED_KEY,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "lastUsedProject" });
      return result.ok ? result.value : null;
    },
  });

  const recent = useQuery<string[]>({
    queryKey: RECENT_KEY,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "recentProjects" });
      return result.ok ? result.value : [];
    },
  });

  // Staff browse every mandate but file only into those they work: an unseated member was offered — and defaulted
  // into — positions whose every save came back "not on that position's team". `undefined` is an older API.
  const live = useMemo(
    () => (projects.data ?? []).filter((project) => !project.archivedAt && project.viewerCanWork !== false),
    [projects.data],
  );

  // Chooses the default once every answer is in, and only while nothing is selected — so a
  // consultant who has already picked a position never has it changed under them by a late query.
  useEffect(() => {
    if (selectedProjectId || !live.length || lastUsed.isPending || !hasReadSettings) {
      return;
    }
    const preferred = [settings.defaultProjectId, lastUsed.data]
      .map((wanted) => live.find((project) => project.id === wanted))
      .find(Boolean);
    setSelectedProjectId(preferred?.id ?? live[0].id);
  }, [live, lastUsed.data, lastUsed.isPending, hasReadSettings, settings.defaultProjectId, selectedProjectId]);

  const selectProject = (projectId: string) => {
    setSelectedProjectId(projectId);
    // The cache as well as storage: `staleTime` means the old answer would otherwise be served for
    // another half-minute, and the next panel open would offer a position the consultant moved off.
    queryClient.setQueryData(LAST_USED_KEY, projectId);
    queryClient.setQueryData<string[]>(RECENT_KEY, (held) =>
      [projectId, ...(held ?? []).filter((id) => id !== projectId)].slice(0, 3),
    );
    void askServiceWorker({ kind: "rememberProject", projectId });
  };

  const selectedProject = live.find((project) => project.id === selectedProjectId) ?? null;

  return {
    projects: live,
    recentProjectIds: recent.data ?? [],
    selectedProjectId: selectedProject?.id ?? null,
    selectedProject,
    selectProject,
    isLoading: projects.isPending || lastUsed.isPending,
  };
}

export type ProjectSelection = ReturnType<typeof useProjectSelection>;
