import { useQuery } from "@tanstack/react-query";
import * as authApi from "./api/authApi";

/**
 * The sign-in methods and the human check's site key, one fetch shared by every sign-in screen. Neither changes while
 * the page is open, hence no refetching.
 */
export const authProvidersQuery = {
  queryKey: ["auth", "providers"],
  queryFn: authApi.providers,
  staleTime: Infinity,
  retry: false,
} as const;

export function useAuthProviders() {
  return useQuery(authProvidersQuery);
}
