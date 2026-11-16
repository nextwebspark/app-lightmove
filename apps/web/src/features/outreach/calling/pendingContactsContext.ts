import { createContext, useContext, type MouseEvent } from "react";
import type { CandidatePhone } from "../../candidates/api/types";
import type { DialTarget } from "../../contactlookup/lib/phoneReach";
import type { PendingChannel, PendingContact } from "./pendingContactStore";

export interface PendingContactsValue {
  pending: readonly PendingContact[];
  /**
   * A press of a phone's Call, WhatsApp or Copy. `go` does what the press does once confirmed; without a caution the
   * press goes ahead on its own and only leaves its card.
   */
  reachOut: (
    event: MouseEvent | null,
    channel: PendingChannel,
    phone: CandidatePhone,
    target: DialTarget,
    go: () => void,
  ) => void;
  settle: (id: string) => void;
  markWrongNumber: (id: string) => void;
}

export const PendingContactsContext = createContext<PendingContactsValue | null>(null);

/** Null outside the provider (a client seat's shell, a test): a phone then works as a plain link and leaves nothing. */
export function usePendingContacts(): PendingContactsValue | null {
  return useContext(PendingContactsContext);
}
