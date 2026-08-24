import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { ProjectSummary } from "../../api/types";

/**
 * The mandate a capture will land in, and the dropdown that chooses it.
 *
 * Defaults to the last one used — remembered in extension storage by the service worker — because a
 * consultant working a search captures a dozen companies into the same mandate in a row, and choosing
 * it again each time is the kind of friction that stops a tool being used.
 */
export function useProjectSelection() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);

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
    const remembered = projects.data.find((project) => project.id === lastUsed.data);
    setSelectedProjectId(remembered?.id ?? projects.data[0].id);
  }, [projects.data, lastUsed.data, selectedProjectId]);

  const selectProject = (projectId: string) => {
    setSelectedProjectId(projectId);
    void askServiceWorker({ kind: "rememberProject", projectId });
  };

  return {
    projects: projects.data ?? [],
    selectedProjectId,
    selectProject,
    isLoading: projects.isPending || lastUsed.isPending,
  };
}
