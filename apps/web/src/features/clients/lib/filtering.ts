import type { Client } from "../api/types";

/**
 * The Clients screen's list logic — chips and search — extracted pure so it is testable without a DOM,
 * mirroring the projects feature's own filtering module.
 */

export const CHIPS = [
  { key: "all", label: "All clients" },
  { key: "active", label: "Active mandates" },
  { key: "noreps", label: "No representative" },
] as const;

export type ChipKey = (typeof CHIPS)[number]["key"];

export function filterClients(
  clients: Client[],
  options: { chip: ChipKey; query: string },
): Client[] {
  const query = options.query.trim().toLowerCase();

  return clients.filter((client) => {
    if (options.chip === "active" && client.activeMandates === 0) return false;
    if (options.chip === "noreps" && client.contacts.length > 0) return false;
    if (query && !`${client.name} ${client.sector ?? ""}`.toLowerCase().includes(query)) {
      return false;
    }
    return true;
  });
}
