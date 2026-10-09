import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as mailboxApi from "../api/mailboxApi";

/**
 * The zone a consultant's sending hours and daily cap are read in. Defaults to Dubai on the server, so a
 * consultant elsewhere changes it once; sends already due keep their time.
 */
/** The zones the server takes: a region's own, or UTC — never a legacy alias. */
const REGION_ZONE = /^(UTC|(Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)\/.+)$/;

export function MailboxTimeZoneSelect({ timeZone }: { timeZone: string }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const zones = useMemo(() => {
    const listed = Intl.supportedValuesOf("timeZone").filter((zone) => REGION_ZONE.test(zone));
    return listed.includes(timeZone) ? listed : [...listed, timeZone].sort();
  }, [timeZone]);
  const change = useMutation({
    mutationFn: mailboxApi.changeMailboxTimeZone,
    onSuccess: (mailbox) => {
      queryClient.setQueryData(mailboxApi.MAILBOX_KEY, mailbox);
      toast("Time zone saved. Your sending hours now read in it.");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <select
      value={timeZone}
      aria-label="Sending time zone"
      title="Your sequences' sending hours are read in this time zone"
      disabled={change.isPending}
      onChange={(event) => change.mutate(event.target.value)}
      className="max-w-[190px] rounded-[7px] border border-u-border bg-u-raised px-2 py-1.5 font-mono text-[12px] text-u-text2"
    >
      {zones.map((zone) => (
        <option key={zone} value={zone}>
          {zone.replace(/_/g, " ")}
        </option>
      ))}
    </select>
  );
}
