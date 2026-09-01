import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { askServiceWorker } from "../../background/extensionMessages";
import type { WorkspaceUser } from "../../api/types";
import { SESSION_KEY as STORED_SESSION_KEY } from "../../background/storageKeys";

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

  // The panel stays open while the consultant pairs in another tab, so the session arrives while this
  // is on screen. Watching the store is what turns that into the capture form without a second click.
  const { refetch } = session;
  useEffect(() => {
    const onStored = (changes: Record<string, chrome.storage.StorageChange>, area: string) => {
      if (area === "local" && STORED_SESSION_KEY in changes) {
        void refetch();
      }
    };
    chrome.storage.onChanged.addListener(onStored);
    return () => chrome.storage.onChanged.removeListener(onStored);
  }, [refetch]);

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
