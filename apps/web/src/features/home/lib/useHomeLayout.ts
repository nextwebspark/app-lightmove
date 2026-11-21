import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import * as homeApi from "../api/homeApi";
import {
  DEFAULT_HOME_LAYOUT,
  forgetLegacyLayout,
  isDefaultLayout,
  legacyLayout,
  rememberLanding,
  type HomeLayout,
  type HomeOwner,
} from "./homeLayout";

/**
 * The person's Home in this workspace, kept on the server so it follows them to every browser: their saved layout,
 * else one they kept on this browser before (carried over on their first save), else the default. Saving the default
 * forgets the saved one, so they go on following the default as it changes.
 */
export function useHomeLayout(owner: HomeOwner) {
  const queryClient = useQueryClient();
  const read = useQuery({
    queryKey: homeApi.HOME_LAYOUT_KEY,
    queryFn: ({ signal }) => homeApi.getHomeLayout(signal),
    staleTime: 5 * 60_000,
  });
  // A failed read draws the default rather than holding Home back: the layout is a preference, not the content.
  const layout: HomeLayout = read.data ?? legacyLayout(owner) ?? DEFAULT_HOME_LAYOUT;
  const known = read.isSuccess;

  const { userId, workspaceId } = owner;
  useEffect(() => {
    if (known) rememberLanding({ userId, workspaceId }, layout.landing);
  }, [known, layout.landing, userId, workspaceId]);

  const save = useMutation({
    mutationFn: async (next: HomeLayout) => {
      if (isDefaultLayout(next)) {
        await homeApi.resetHomeLayout();
        return null;
      }
      return homeApi.saveHomeLayout(next);
    },
    onSuccess: (saved, next) => {
      queryClient.setQueryData(homeApi.HOME_LAYOUT_KEY, saved);
      rememberLanding(owner, next.landing);
      forgetLegacyLayout(owner);
    },
  });

  return { layout, pending: read.isPending, save };
}
