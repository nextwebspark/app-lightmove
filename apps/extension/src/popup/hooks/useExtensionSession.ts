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

  // Not paired and "the worker did not answer" are different answers, and collapsing them to null was
  // the expensive kind of wrong: the popup showed the signed-out screen, whose one action re-pairs —
  // and a re-pair revokes the session the extension was still holding perfectly well.
  const session = useQuery<WorkspaceUser | null>({
    queryKey: SESSION_KEY,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "getPairedUser" });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
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
    hasFailed: session.isError,
    failure: session.error instanceof Error ? session.error.message : null,
    isLoading: session.isPending,
    refresh: session.refetch,
    signOut,
  };
}
