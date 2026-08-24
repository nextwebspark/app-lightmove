import { useQuery, useQueryClient } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { WorkspaceUser } from "../../api/types";

const SESSION_KEY = ["extension", "session"] as const;

/**
 * Who the extension is paired as, or null when it is not paired.
 *
 * The popup never sees a token — it asks the service worker who the session belongs to and gets a name
 * and an email back. That is all it needs to render the header, and all it should ever hold.
 */
export function useExtensionSession() {
  const queryClient = useQueryClient();

  const session = useQuery<WorkspaceUser | null>({
    queryKey: SESSION_KEY,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "getPairedUser" });
      return result.ok ? result.value : null;
    },
  });

  const signOut = async () => {
    await askServiceWorker({ kind: "signOut" });
    // The whole cache, not just the session: every other entry was read with the credential that has
    // just been revoked, and showing a stale project list to a signed-out popup would be a lie.
    queryClient.clear();
    await session.refetch();
  };

  return {
    user: session.data ?? null,
    isPaired: Boolean(session.data),
    isLoading: session.isPending,
    refresh: session.refetch,
    signOut,
  };
}
