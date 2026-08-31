import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { ProjectSummary } from "../../api/types";
import { useCaptureSettings } from "./useCaptureSettings";

/**
 * The mandate a capture will land in, and the dropdown that chooses it.
 *
 * Defaults to the mandate chosen in Settings, or failing that the last one used — because a consultant
 * working a search captures a dozen companies into the same mandate in a row, and choosing it again
 * each time is the kind of friction that stops a tool being used.
 */
export function useProjectSelection() {
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
    queryKey: ["extension", "lastUsedProject"],
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
