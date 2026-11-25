import { useQuery } from "@tanstack/react-query";
import * as authApi from "./api/authApi";

/** The sign-in methods, one fetch shared by every sign-in screen; they never change while the page is open. */
export const authProvidersQuery = {
  queryKey: ["auth", "providers"],
  queryFn: authApi.providers,
  staleTime: Infinity,
  retry: false,
} as const;

export function useAuthProviders() {
  return useQuery(authProvidersQuery);
}
