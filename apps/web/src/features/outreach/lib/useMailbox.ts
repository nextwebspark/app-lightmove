import { useQuery } from "@tanstack/react-query";
import * as mailboxApi from "../api/mailboxApi";

/**
 * The caller's own mailbox: whether outreach is offered here, and what it would send from. Staff only —
 * the route refuses a client seat, so a screen both read passes `enabled` rather than asking for a 403.
 */
export function useMailbox(enabled = true) {
  return useQuery({
    queryKey: mailboxApi.MAILBOX_KEY,
    queryFn: ({ signal }) => mailboxApi.getMailbox(signal),
    enabled,
  });
}
