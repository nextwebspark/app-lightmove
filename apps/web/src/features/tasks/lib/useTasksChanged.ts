import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import { POOL_KEY } from "../../candidates/api/poolApi";
import { TASKS_KEY_PREFIX } from "../api/tasksApi";

/**
 * Everything a task write leaves stale: every task read, and the position's people — the pipeline's Next
 * action and the person's timeline hang under that prefix. `keepMyTasks` leaves the open My tasks list
 * standing (marked stale, not refetched), so the row just ticked stays struck where it was.
 */
export function useTasksChanged() {
  const queryClient = useQueryClient();
  return useCallback(
    (projectId: string, { keepMyTasks = false }: { keepMyTasks?: boolean } = {}) => {
      void queryClient.invalidateQueries({
        queryKey: TASKS_KEY_PREFIX,
        refetchType: keepMyTasks ? "none" : "active",
      });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      void queryClient.invalidateQueries({ queryKey: [...POOL_KEY, "timeline"] });
    },
    [queryClient],
  );
}
