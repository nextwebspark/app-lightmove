import { request } from "../../../../lib/apiClient";
import type { ApplicationVariable, VariableChange } from "./types";

/** Platform → Variables: the values the application runs on that are changed without a release. */
export const VARIABLES_KEY = ["platform-variables"] as const;
export const VARIABLE_HISTORY_KEY = (key: string) => ["platform-variables", key, "history"] as const;

const variableUrl = (key: string) => `/platform/settings/${encodeURIComponent(key)}`;

export function listVariables(signal?: AbortSignal): Promise<ApplicationVariable[]> {
  return request<ApplicationVariable[]>("/platform/settings", { signal });
}

export function getVariableHistory(key: string, signal?: AbortSignal): Promise<VariableChange[]> {
  return request<VariableChange[]>(`${variableUrl(key)}/history`, { signal });
}

/** The value goes as typed: the server refuses one that is not a number on its field, with the variable's range. */
export function changeVariable(key: string, value: string, note: string): Promise<ApplicationVariable> {
  return request<ApplicationVariable>(variableUrl(key), { method: "PUT", body: { value, note } });
}

export function resetVariable(key: string, note: string): Promise<ApplicationVariable> {
  return request<ApplicationVariable>(`${variableUrl(key)}/reset`, { method: "POST", body: { note } });
}
