import { useQuery } from "@tanstack/react-query";
import { DEFAULT_CURRENCY } from "../../../lib/currencies";
import * as workspaceApi from "../api/workspaceApi";

export interface WorkspaceCurrency {
  /** What a new package, brief or template starts in; the default only until the read settles or if it is refused. */
  currency: string;
  /** Whether the read has answered, or failed: a value seeded once must wait for this, or it seeds the default. */
  isSettled: boolean;
}

/** The workspace's currency, set from the firm's country and changed in Settings → General. */
export function useWorkspaceCurrency(): WorkspaceCurrency {
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });
  return {
    currency: workspace.data?.defaultCurrency || DEFAULT_CURRENCY,
    isSettled: !workspace.isPending,
  };
}
