import { useQuery } from "@tanstack/react-query";
import type { AiControls } from "../api/types";
import * as workspaceApi from "../api/workspaceApi";

/**
 * What the AI may do with the workspace's people, or null until the read answers. Null is treated as allowed: the
 * server refuses what is switched off, so a screen only hides a button once it knows.
 */
export function useAiControls(): AiControls | null {
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });
  return workspace.data?.aiControls ?? null;
}
