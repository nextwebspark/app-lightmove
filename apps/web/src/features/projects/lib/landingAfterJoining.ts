import type { QueryClient } from "@tanstack/react-query";
import { isPureClient } from "../../auth/roles";
import type { User } from "../../auth/api/types";
import * as projectsApi from "../api/projectsApi";
import { isActive } from "./filtering";

/**
 * Where someone who just joined a workspace lands: the one open position they were put on, when there is exactly
 * one, so their first screen is the work they were asked in for; otherwise My positions.
 */
export async function landingAfterJoining(user: User, queryClient: QueryClient): Promise<string> {
  try {
    // Through the cache, so My positions — where most people land — doesn't ask again.
    const positions = await queryClient.fetchQuery({ queryKey: projectsApi.PROJECTS_KEY, queryFn: projectsApi.projects });
    // A pure client's list is already only the positions shared with them; staff see every position, so theirs are
    // the ones they hold a seat on.
    const mine = isPureClient(user.workspace?.roles ?? [])
      ? positions
      : positions.filter((position) => position.team.some((seat) => seat.userId === user.id));
    const open = mine.filter(isActive);
    return open.length === 1 ? `/projects/${open[0].id}` : "/";
  } catch {
    return "/";
  }
}
