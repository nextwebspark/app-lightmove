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

/** Whether the caller is a workspace admin: the one who may change the plan or buy credits. */
export function useIsWorkspaceAdmin(): boolean {
  const { user } = useAuth();
  return user?.workspace?.roles.includes("ADMIN") ?? false;
}
