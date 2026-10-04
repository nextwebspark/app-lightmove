import type { User, WorkspaceSummary } from "./api/types";

/**
 * Why a tab restarted after a refresh moved its session. It has to outlive the reload that follows,
 * so it waits in sessionStorage and is shown once by whichever screen renders the topbar next.
 */
const NOTICE_KEY = "lm.workspace.moved";

export function describeWorkspaceMove(left: WorkspaceSummary | null, moved: User): string {
  if (!moved.workspace) {
    return `You no longer have access to ${left?.name ?? "that workspace"}.`;
  }
  const stillMember = moved.workspaces.some((workspace) => workspace.id === left?.id);
  if (!left || stillMember) {
    return `You're now in ${moved.workspace.name}.`;
  }
  return `You no longer have access to ${left.name} — you're now in ${moved.workspace.name}.`;
}

export function rememberWorkspaceMove(message: string): void {
  try {
    sessionStorage.setItem(NOTICE_KEY, message);
  } catch {
    // A private window may refuse storage; the move itself still happens, just unexplained.
  }
}

export function takeWorkspaceMove(): string | null {
  try {
    const message = sessionStorage.getItem(NOTICE_KEY);
    sessionStorage.removeItem(NOTICE_KEY);
    return message;
  } catch {
    return null;
  }
}
