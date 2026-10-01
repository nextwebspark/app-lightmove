import { useQuery } from "@tanstack/react-query";
import * as mailboxApi from "../api/mailboxApi";

/** The caller's own mailbox: whether outreach is offered here, and what it would send from. */
export function useMailbox() {
  return useQuery({
    queryKey: mailboxApi.MAILBOX_KEY,
    queryFn: ({ signal }) => mailboxApi.getMailbox(signal),
  });
}
