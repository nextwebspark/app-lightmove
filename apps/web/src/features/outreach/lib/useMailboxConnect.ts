import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useToast } from "../../../components/ui";
import { messageForCode } from "../../../lib/errorCodes";
import * as mailboxApi from "../api/mailboxApi";
import { connectMailboxInPopup } from "./mailboxPopup";

/**
 * Connects, or reconnects, the consultant's mailbox in a popup, for whichever control on the page asked. One popup
 * at a time: asking again abandons the one still open, and leaving the page abandons it too.
 */
export function useMailboxConnect() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [connectingProvider, setConnectingProvider] = useState<string | null>(null);
  const abandonConnect = useRef<() => void>(() => {});

  useEffect(() => () => abandonConnect.current(), []);

  const connect = (provider: string) => {
    abandonConnect.current();
    setConnectingProvider(provider);
    const finish = () => setConnectingProvider(null);
    abandonConnect.current = connectMailboxInPopup(
      async () => (await mailboxApi.startMailboxConnect(provider)).authorizationUrl,
      {
        onConnected: () => {
          finish();
          toast("Mailbox connected.");
          void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
        },
        onError: (code) => {
          finish();
          toast.error(messageForCode(code));
        },
        onCancel: finish,
      },
    );
  };

  return { connectingProvider, connect };
}

export type MailboxConnect = ReturnType<typeof useMailboxConnect>;
