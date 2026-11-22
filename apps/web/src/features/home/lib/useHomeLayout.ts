import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { codeOf } from "../../../lib/errorCodes";
import * as homeApi from "../api/homeApi";
import {
  DEFAULT_HOME_LAYOUT,
  forgetLegacyLayout,
  isDefaultLayout,
  legacyLayout,
  type HomeLayout,
  type HomeOwner,
} from "./homeLayout";

/**
 * The person's Home in this workspace, kept on the server so it follows them to every browser: their saved layout,
 * else one they kept on this browser before (carried over on their first save), else the default. Saving the default
 * forgets the saved one, so they go on following the default as it changes. A save goes over the version this window
 * read; one made elsewhere since is refused and read again rather than overwritten.
 */
export function useHomeLayout(owner: HomeOwner) {
  const queryClient = useQueryClient();
  const read = useQuery({
    queryKey: homeApi.HOME_LAYOUT_KEY,
    queryFn: ({ signal }) => homeApi.getHomeLayout(signal),
    staleTime: 5 * 60_000,
  });
  // A failed read draws the default rather than holding Home back: the layout is a preference, not the content.
  const layout: HomeLayout = read.data?.layout ?? legacyLayout(owner) ?? DEFAULT_HOME_LAYOUT;
  const version = read.data?.version ?? null;
  const { userId, workspaceId } = owner;

  const save = useMutation({
    mutationFn: async (next: HomeLayout) => {
      if (isDefaultLayout(next)) {
        await homeApi.resetHomeLayout(version);
        return null;
      }
      return homeApi.saveHomeLayout(next, version);
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(homeApi.HOME_LAYOUT_KEY, saved);
      forgetLegacyLayout({ userId, workspaceId });
    },
    onError: (error) => {
      if (codeOf(error) === "HOME_LAYOUT_STALE") void queryClient.invalidateQueries({ queryKey: homeApi.HOME_LAYOUT_KEY });
    },
  });

  return { layout, pending: read.isPending, save };
}
