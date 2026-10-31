import { useQuery } from "@tanstack/react-query";
import * as candidatesApi from "../api/candidatesApi";

/**
 * Everyone mapped at a company of the position, on every stage; undefined until read. A refused read counts as
 * nobody: the server refuses a removal taking more than it was told, so guessing low removes no one unannounced.
 */
export function useExecutivesAtCompany(projectId: string, triageCompanyId: string | null): number | undefined {
  const scope = { triageCompanyIds: triageCompanyId ? [triageCompanyId] : [] };
  const { data, isError } = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(projectId, scope),
    queryFn: ({ signal }) => candidatesApi.getCandidates(projectId, scope, signal),
    enabled: triageCompanyId !== null,
  });
  if (triageCompanyId === null) return undefined;
  if (isError) return 0;
  return data?.totalCount;
}
