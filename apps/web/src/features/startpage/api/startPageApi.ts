import { request } from "../../../lib/apiClient";
import type { SaveStartPage, StartPage } from "./types";

/** The caller's own start page in this workspace; the session's `/me` carries the same answer. */
export function saveStartPage(body: SaveStartPage): Promise<StartPage> {
  return request<StartPage>("/me/start-page", { method: "PUT", body });
}
