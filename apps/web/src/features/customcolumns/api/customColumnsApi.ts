import { request } from "../../../lib/apiClient";
import type {
  CustomColumn,
  CustomColumnsResponse,
  DefineCustomColumnPayload,
  UpdateCustomColumnPayload,
} from "./types";

export const CUSTOM_COLUMNS_KEY_PREFIX = "custom-columns";

export const CUSTOM_COLUMNS_KEY = (projectId: string) =>
  [CUSTOM_COLUMNS_KEY_PREFIX, projectId] as const;

const base = (projectId: string) => `/projects/${projectId}/custom-columns`;

/**
 * Both grids' columns in one read. One request rather than two because the Companies screen is one
 * screen: a row is a person at a company, so it needs both halves to build a single header row.
 */
export function getCustomColumns(projectId: string): Promise<CustomColumnsResponse> {
  return request<CustomColumnsResponse>(base(projectId));
}

export function defineCustomColumn(
  projectId: string,
  payload: DefineCustomColumnPayload,
): Promise<CustomColumn> {
  return request<CustomColumn>(base(projectId), { method: "POST", body: payload });
}

export function updateCustomColumn(
  projectId: string,
  columnId: string,
  payload: UpdateCustomColumnPayload,
): Promise<CustomColumn> {
  return request<CustomColumn>(`${base(projectId)}/${columnId}`, {
    method: "PATCH",
    body: payload,
   
  });
}

/** The whole new order of one grid's columns — a drag moves every position after the one that moved. */
export function reorderCustomColumns(
  projectId: string,
  columnIds: string[],
): Promise<CustomColumnsResponse> {
  return request<CustomColumnsResponse>(`${base(projectId)}/order`, {
    method: "PUT",
    body: { columnIds },
   
  });
}

/** Removes the definition only. The rows keep their values, so an accidental delete is recoverable. */
export function deleteCustomColumn(projectId: string, columnId: string): Promise<void> {
  return request<void>(`${base(projectId)}/${columnId}`, { method: "DELETE" });
}
