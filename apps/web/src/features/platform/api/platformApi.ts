import { request } from "../../../lib/apiClient";

/** The fresh two-factor code a high-impact platform action asks for: six digits from the app, or a recovery code. */
export function confirmStepUp(code: string): Promise<void> {
  return request<void>("/platform/step-up", { method: "POST", body: { code } });
}
