import type { WorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import type { Client } from "../api/types";

/**
 * The Clients screen's list logic — chips and search — extracted pure so it is testable without a DOM,
 * mirroring the projects feature's own filtering module.
 */

export const CLIENT_CHIP_KEYS = ["all", "active", "noreps"] as const;

export type ChipKey = (typeof CLIENT_CHIP_KEYS)[number];

export function chipsFor(vocabulary: WorkspaceVocabulary): { key: ChipKey; label: string }[] {
  return [
    { key: "all", label: `All ${vocabulary.unitsLower}` },
    { key: "active", label: "Active positions" },
    { key: "noreps", label: `No ${vocabulary.contactLower}` },
  ];
}

export function filterClients(
  clients: Client[],
  options: { chip: ChipKey; query: string },
): Client[] {
  const query = options.query.trim().toLowerCase();

  return clients.filter((client) => {
    if (options.chip === "active" && client.activeMandates === 0) return false;
    if (options.chip === "noreps" && client.contacts.length > 0) return false;
    if (query && !client.name.toLowerCase().includes(query)) return false;
    return true;
  });
}
