import { useQuery } from "@tanstack/react-query";
import { DEFAULT_CURRENCY } from "../../../lib/currencies";
import * as workspaceApi from "../api/workspaceApi";

/**
 * The currency a new package, brief or template starts in: the workspace's, set from the firm's country and
 * changed in Settings → General. The default stands only while that read is in flight or refused.
 */
export function useWorkspaceCurrency(): string {
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });
  return workspace.data?.defaultCurrency || DEFAULT_CURRENCY;
}
