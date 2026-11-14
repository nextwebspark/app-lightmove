import { useAuth } from "./AuthProvider";

/** Whether the caller holds the workspace's ADMIN role in this session: who may change the plan or the firm's settings. */
export function useIsWorkspaceAdmin(): boolean {
  const { user } = useAuth();
  return user?.workspace?.roles.includes("ADMIN") ?? false;
}
