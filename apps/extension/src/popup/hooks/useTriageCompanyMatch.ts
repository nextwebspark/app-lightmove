import { useQuery } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { TriageCompanyMatch } from "../../api/types";

/**
 * The company in this mandate a captured person should be mapped to, if the mandate holds one.
 *
 * A candidate belongs to the project and only optionally to a triaged company, so an unmatched
 * employer is filed as text rather than refused. The worker does the matching: it holds the session,
 * and keeping it there is what stops the popup and the worker sharing a bundle.
 */
export function useTriageCompanyMatch(projectId: string | null, employerName: string) {
  const wanted = employerName.trim();

  const match = useQuery<TriageCompanyMatch | null>({
    queryKey: ["extension", "triageMatch", projectId, wanted.toLowerCase()],
    enabled: Boolean(projectId && wanted),
    queryFn: async () => {
      const result = await askServiceWorker({
        kind: "findTriageCompany",
        projectId: projectId!,
        companyName: wanted,
      });
      return result.ok ? result.value : null;
    },
  });

  return { match: match.data ?? null, isMatching: match.isFetching };
}
