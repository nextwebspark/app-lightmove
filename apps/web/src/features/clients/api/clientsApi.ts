import { request } from "../../../lib/apiClient";
import type {
  Client,
  ClientDetail,
  ClientRepresentative,
  CompanyHit,
  CreateClientPayload,
  InviteRepresentativePayload,
  PortalClient,
  UpdateClientPayload,
} from "./types";

/** Every call the clients feature makes, plus the query keys its screens share. */

export const CLIENTS_KEY = ["clients"] as const;
export const clientKey = (clientId: string) => ["clients", clientId] as const;
export const PORTAL_KEY = ["portal"] as const;

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

/** The New-client company search reuses the shared universe endpoint (gated PROJECT_BROWSE). */
export function searchCompanies(query: string): Promise<CompanyHit[]> {
  return request<{ companies: CompanyHit[] }>(
    `/companies/search?q=${encodeURIComponent(query)}`,
  ).then((response) => response.companies);
}

/** The representative's portal read — their own client and its mandates. */
export function portalClient(): Promise<PortalClient> {
  return request<PortalClient>("/portal/me");
}
