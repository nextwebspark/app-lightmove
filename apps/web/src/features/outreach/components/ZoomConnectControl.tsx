import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor, messageForCode } from "../../../lib/errorCodes";
import * as zoomApi from "../api/zoomApi";
import { connectMailboxInPopup } from "../lib/mailboxPopup";

/**
 * The consultant's own Zoom account, on the Outreach page and in the drawer's Meetings section: Connect Zoom,
 * Zoom connected with Disconnect, or Reconnect Zoom once Zoom refused the stored token. Draws nothing where the
 * workspace has no Zoom app. Connecting runs in the same popup a mailbox does.
 */
export function ZoomConnectControl({ className }: { className?: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isConnecting, setIsConnecting] = useState(false);
  const abandonConnect = useRef<() => void>(() => {});
  const zoom = useQuery({ queryKey: zoomApi.ZOOM_KEY, queryFn: ({ signal }) => zoomApi.getZoom(signal) });

  useEffect(() => () => abandonConnect.current(), []);

  /** Book a call's grid says whether Zoom is offered, so every outreach read is asked again. */
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["outreach"] });

  const disconnect = useMutation({
    mutationFn: zoomApi.disconnectZoom,
    onSuccess: () => {
      toast("Zoom disconnected.");
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleConnect = () => {
    abandonConnect.current();
    setIsConnecting(true);
    const finish = () => setIsConnecting(false);
    abandonConnect.current = connectMailboxInPopup(async () => (await zoomApi.startZoomConnect()).authorizationUrl, {
      onConnected: () => {
        finish();
        toast("Zoom connected.");
        refresh();
      },
      onError: (code) => {
        finish();
        toast.error(messageForCode(code));
      },
      onCancel: finish,
    });
  };

  if (!zoom.isSuccess || !zoom.data.offered) {
    return null;
  }

  if (zoom.data.status === "ACTIVE") {
    return (
      <div className={cn("flex items-center gap-2 text-[12px] text-u-text2", className)}>
        <span className="inline-flex items-center gap-1.5 font-mono">
          <span aria-hidden="true" className="size-1.5 rounded-full bg-u-direct" />
          Zoom connected
        </span>
        <Button
          variant="ghost"
          className="px-2 py-1 text-[12px]"
          loading={disconnect.isPending}
          onClick={() => disconnect.mutate()}
        >
          Disconnect
        </Button>
      </div>
    );
  }

  const isWithdrawn = zoom.data.status === "ERROR";
  return (
    <div className={cn("flex items-center gap-2 text-[12px]", className)}>
      {isWithdrawn && <span className="text-u-offlimits">Zoom needs reconnecting.</span>}
      <Button variant="secondary" className="px-[11px] py-[5px] text-[12px]" loading={isConnecting} onClick={handleConnect}>
        {isWithdrawn ? "Reconnect Zoom" : "Connect Zoom"}
      </Button>
    </div>
  );
}
