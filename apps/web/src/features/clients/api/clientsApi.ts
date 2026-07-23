import { request } from "../../../lib/apiClient";
import type {
  Client,
  ClientDetail,
  ClientRepresentative,
  CreateClientPayload,
  InviteRepresentativePayload,
  UpdateClientPayload,
} from "./types";

/** Every call the clients feature makes, plus the query keys its screens share. */

export const CLIENTS_KEY = ["clients"] as const;
export const clientKey = (clientId: string) => ["clients", clientId] as const;

export function clients(): Promise<Client[]> {
  return request<Client[]>("/clients");
}

export function client(clientId: string): Promise<ClientDetail> {
  return request<ClientDetail>(`/clients/${clientId}`);
}

export function createClient(payload: CreateClientPayload): Promise<Client> {
  return request<Client>("/clients", { method: "POST", body: payload });
}

export function updateClient(clientId: string, payload: UpdateClientPayload): Promise<ClientDetail> {
  return request<ClientDetail>(`/clients/${clientId}`, { method: "PATCH", body: payload });
}

export function inviteRepresentative(
  clientId: string,
  payload: InviteRepresentativePayload,
): Promise<ClientRepresentative> {
  return request<ClientRepresentative>(`/clients/${clientId}/representatives`, {
    method: "POST",
    body: payload,
  });
}
