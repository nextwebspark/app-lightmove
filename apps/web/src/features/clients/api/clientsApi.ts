import { request } from "../../../lib/apiClient";
import type { CompanyPick } from "../lib/companyPick";
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

/**
 * The body a picked company becomes. A universe pick sends its account id and nothing the client saw:
 * the server re-resolves the canonical name and domain, so a client cannot be filed under a name of
 * its own choosing.
 */
export function createClientPayloadFor(pick: CompanyPick): CreateClientPayload {
  return pick.source === "universe"
    ? {
        company: { apolloAccountId: pick.company.apolloAccountId },
        sector: pick.company.industry ?? undefined,
      }
    : { customName: pick.name, customDomain: pick.domain || undefined };
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
