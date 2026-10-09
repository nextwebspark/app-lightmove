import { useMutation, useQueryClient } from "@tanstack/react-query";
import { SegmentedControl, useToast, type SegmentedOption } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import type { CalendarSync } from "../../workspace/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";

const OPTIONS: readonly SegmentedOption<CalendarSync>[] = [
  { value: "RECALL", label: "Recall" },
  { value: "DIRECT", label: "Direct" },
];

/**
 * How calendar events are read. The disclosure is the card's whole point: on Recall the app's keys and every
 * consultant's calendar refresh token leave for a sub-processor, and an admin decides that knowingly.
 */
export function CalendarSyncCard({ calendarSync, recallOffered }: { calendarSync: CalendarSync; recallOffered: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const change = useMutation({
    mutationFn: (next: CalendarSync) => workspaceApi.changeCalendarSync(next),
    onSuccess: (saved) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, saved);
      toast(saved.calendarSync === "RECALL" ? "Calendars sync through Recall" : "Calendars are read directly");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleChoose = (next: CalendarSync) => {
    if (next !== calendarSync && !change.isPending) change.mutate(next);
  };

  return (
    <section aria-label="Calendar sync" className="rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="mb-3.5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-[12rem] flex-1">
          <div className="text-sm font-semibold">Calendar sync</div>
          <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">How meetings with candidates reach their drawer.</div>
        </div>
        <SegmentedControl label="Calendar sync" options={OPTIONS} value={calendarSync} onChange={handleChoose} />
      </div>
      <p className="rounded-lg border border-u-border bg-u-surface px-3.5 py-3 text-note text-u-text2">
        {calendarSync === "RECALL" ? (
          <>
            Calendar changes arrive as they happen, through Recall.ai. To do that,{" "}
            <strong className="font-semibold text-u-text">
              the app's client ID and secret and each person's calendar refresh token are shared with Recall.ai
            </strong>
            . Choose Direct to keep them here.
          </>
        ) : (
          <>
            Nothing is shared with Recall.ai: calendars are read here, and only when a candidate's drawer opens, so a
            change made in a calendar shows up the next time someone looks.
          </>
        )}
      </p>
      {!recallOffered && (
        <p className="mt-2.5 font-mono text-[11.5px] text-u-text3">
          Recall isn't set up on this deployment yet, so calendars are read directly whichever you choose.
        </p>
      )}
    </section>
  );
}
