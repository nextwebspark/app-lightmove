import { useQuery } from "@tanstack/react-query";
import * as candidatesApi from "../api/candidatesApi";

/**
 * Everyone mapped at one of the position's companies, on every stage — what removing the company would take. Read
 * only while asked (a company id given); undefined until the read lands. A refused read counts as nobody: the server
 * refuses a removal that would take anyone it was not told about, so guessing low never removes someone unannounced.
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
