import { request } from "../../../lib/apiClient";
import type { WorkspaceDetail } from "../../workspace/api/types";
import type { WorkspaceTwoFactor } from "./types";

export const WORKSPACE_TWO_FACTOR_KEY = ["workspace", "two-factor"] as const;

/** Who in the workspace has two-factor authentication, pure clients included; WORKSPACE_MANAGE alone may ask. */
export function workspaceTwoFactor(): Promise<WorkspaceTwoFactor> {
  return request<WorkspaceTwoFactor>("/workspace/two-factor");
}

/** Turning it on is refused until the admin has it themselves. */
export function changeRequireMfa(required: boolean): Promise<WorkspaceDetail> {
  return request<WorkspaceDetail>("/workspace/require-mfa", { method: "PUT", body: { required } });
}

/** Signs the member out everywhere and emails them; they set it up again at their next sign-in where it is required. */
export function resetMemberTwoFactor(memberId: string): Promise<void> {
  return request<void>(`/members/${memberId}/two-factor`, { method: "DELETE" });
}
