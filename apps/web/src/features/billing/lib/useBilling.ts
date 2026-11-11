import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as billingApi from "../api/billingApi";

/** The billing read, asked only for staff: a pure client is answered 404, so it is never sent. */
export function useBilling(refetchInterval: number | false = false) {
  const { user } = useAuth();
  const roles = user?.workspace?.roles;
  return useBillingRead(roles !== undefined && !isPureClient(roles), refetchInterval);
}

/** The same read for a caller that already knows it is staff's, such as a button only staff are offered. */
export function useBillingRead(enabled: boolean, refetchInterval: number | false = false) {
  return useQuery({
    queryKey: billingApi.BILLING_KEY,
    queryFn: ({ signal }) => billingApi.getBilling(signal),
    enabled,
    refetchInterval,
  });
}

/**
 * Whether billing is shown to this caller: never to a pure client, and not where the deployment has switched it off.
 * Held back while the read is in flight, so a demo never flashes it; a failed read shows it, the page saying why.
 */
export function useBillingOffered(): { offered: boolean; isLoading: boolean } {
  const billing = useBilling();
  return { offered: billing.data ? billing.data.enforced : billing.isError, isLoading: billing.isLoading };
}

/** Whether the caller is a workspace admin: the one who may change the plan or buy credits. */
export function useIsWorkspaceAdmin(): boolean {
  const { user } = useAuth();
  return user?.workspace?.roles.includes("ADMIN") ?? false;
}
