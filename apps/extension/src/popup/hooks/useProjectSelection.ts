import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { ProjectSummary } from "../../api/types";
import { useCaptureSettings } from "./useCaptureSettings";

/**
 * The mandate a capture will land in. Defaults to the one chosen in Settings, else the last one used.
 * Called once, in `CapturePopup`, and passed to both tabs — two selections file a person into the
 * mandate the consultant did not choose.
 */
const LAST_USED_KEY = ["extension", "lastUsedProject"] as const;

export function useProjectSelection() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const { settings } = useCaptureSettings();

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

  // Chooses the default once both answers are in, and only while nothing is selected — so a
  // consultant who has already picked a mandate never has it changed under them by a late query.
  useEffect(() => {
    if (selectedProjectId || !projects.data?.length) {
      return;
    }
    const preferred = [settings.defaultProjectId, lastUsed.data]
      .map((wanted) => projects.data?.find((project) => project.id === wanted))
      .find(Boolean);
    setSelectedProjectId(preferred?.id ?? projects.data[0].id);
  }, [projects.data, lastUsed.data, settings.defaultProjectId, selectedProjectId]);

  const selectProject = (projectId: string) => {
    setSelectedProjectId(projectId);
    // The cache as well as storage: `staleTime` means the old answer would otherwise be served for
    // another half-minute, and the next popup open would offer a mandate the consultant moved off.
    queryClient.setQueryData(LAST_USED_KEY, projectId);
    void askServiceWorker({ kind: "rememberProject", projectId });
  };

  return {
    projects: projects.data ?? [],
    selectedProjectId,
    selectedProjectName: projects.data?.find((project) => project.id === selectedProjectId)?.positionTitle ?? null,
    selectProject,
    isLoading: projects.isPending || lastUsed.isPending,
  };
}

export type ProjectSelection = ReturnType<typeof useProjectSelection>;
