import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { isPureClient } from "../../auth/roles";
import * as projectsApi from "../../projects/api/projectsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as poolApi from "../api/poolApi";

/**
 * What the Candidates page names things by: the tag catalog, the colleagues who may own someone, and the
 * positions people can be in. Each rides the cache the rest of the app reads it from.
 */
export function usePoolLookups() {
  const tags = useQuery({ queryKey: poolApi.TAGS_KEY, queryFn: ({ signal }) => poolApi.tagCatalog(signal) });
  const members = useQuery({ queryKey: workspaceApi.MEMBERS_KEY, queryFn: workspaceApi.members });
  const projects = useQuery({ queryKey: projectsApi.PROJECTS_KEY, queryFn: projectsApi.projects });

  return useMemo(() => {
    const catalog = tags.data ?? [];
    const staff = (members.data ?? []).filter((member) => !isPureClient(member.roles));
    return {
      tags: catalog,
      offeredTags: catalog.filter((tag) => !tag.retired),
      tagsById: new Map(catalog.map((tag) => [tag.id, tag])),
      staff,
      membersByUserId: new Map((members.data ?? []).map((member) => [member.userId, member])),
      positions: projects.data ?? [],
    };
  }, [tags.data, members.data, projects.data]);
}
