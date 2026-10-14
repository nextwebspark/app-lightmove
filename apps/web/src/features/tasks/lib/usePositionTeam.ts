import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../auth/AuthProvider";
import * as projectsApi from "../../projects/api/projectsApi";
import { canManageProjectAccess } from "../../projects/lib/access";
import type { TaskPerson } from "../api/types";
import { teammatesOf } from "./team";

/**
 * A position's team as the task pickers offer it, read off the cached projects list every shell screen
 * already holds. `canManageTeam` is the lead's (or an admin's): only they are offered "Add to team".
 */
export function usePositionTeam(projectId: string | null, keep?: TaskPerson | null) {
  const { user } = useAuth();
  const projects = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
    enabled: projectId !== null,
  });
  const project = projects.data?.find((candidate) => candidate.id === projectId) ?? null;
  const viewer = user ? { id: user.id, fullName: user.fullName, avatarUrl: user.avatarUrl } : null;

  return {
    project,
    teammates: teammatesOf(project?.team ?? [], viewer, keep),
    viewerId: user?.id ?? null,
    canManageTeam: project ? canManageProjectAccess(project, user?.id, user?.workspace?.roles) : false,
    isLoading: projectId !== null && projects.isPending,
  };
}
