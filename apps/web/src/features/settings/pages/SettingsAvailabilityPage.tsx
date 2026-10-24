import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { MAILBOX_KEY } from "../../outreach/api/mailboxApi";
import * as availabilityApi from "../api/availabilityApi";
import type { MyAvailability } from "../api/availabilityApi";
import { WeeklyHoursEditor } from "../components/WeeklyHoursEditor";
import { draftOf, weekLabelOf, weekProblemOf, workingDaysOf } from "../lib/weeklyHours";

/**
 * Settings → Availability (`Settings.dc.html`, `availability`): the consultant's own working hours and email limit,
 * each either theirs or the workspace's. Their own hours always decide; until they set some, an Outlook calendar's own
 * working hours come before the workspace's, and the page says so.
 */
export function SettingsAvailabilityPage() {
  const availability = useQuery({
    queryKey: availabilityApi.MY_AVAILABILITY_KEY,
    queryFn: ({ signal }) => availabilityApi.getMyAvailability(signal),
  });

  return (
    <>
      <PageHeader
        title="Availability"
        subtitle={
          availability.data
            ? `When your booking link offers times, and how much you send a day · ${availability.data.timeZone}`
            : "When your booking link offers times, and how much you send a day"
        }
      />
      {availability.isError ? (
        <p className="text-note text-u-text3">{messageFor(availability.error)}</p>
      ) : availability.isPending ? (
        <CardsSkeleton />
      ) : (
        <AvailabilityForm key={JSON.stringify(availability.data)} saved={availability.data} />
      )}
    </>
  );
}

function AvailabilityForm({ saved }: { saved: MyAvailability }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [usesWorkspaceHours, setUsesWorkspaceHours] = useState(saved.weeklyHours === null);
  const [week, setWeek] = useState(() => draftOf(saved.weeklyHours ?? saved.workspaceWeeklyHours));
  const [usesWorkspaceCap, setUsesWorkspaceCap] = useState(saved.dailyEmailCap === null);
  const [cap, setCap] = useState(String(saved.dailyEmailCap ?? saved.workspaceDailyEmailCap));

  const capNumber = Number(cap);
  const capProblem =
    !usesWorkspaceCap &&
    (!Number.isInteger(capNumber) || capNumber < 1 || capNumber > saved.workspaceDailyEmailCap)
      ? `Choose a limit from 1 to your workspace's ${saved.workspaceDailyEmailCap}.`
      : null;
  const weekProblem = usesWorkspaceHours ? null : weekProblemOf(week);

  const save = useMutation({
    mutationFn: () =>
      availabilityApi.putMyAvailability({
        weeklyHours: usesWorkspaceHours ? null : workingDaysOf(week),
        dailyEmailCap: usesWorkspaceCap ? null : capNumber,
      }),
    onSuccess: (next) => {
      queryClient.setQueryData(availabilityApi.MY_AVAILABILITY_KEY, next);
      void queryClient.invalidateQueries({ queryKey: MAILBOX_KEY });
      toast("Availability saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <>
      {saved.calendarStatesHours && usesWorkspaceHours && (
        <p className="mb-4 rounded-u-card border border-u-border bg-u-surface px-4 py-3 font-mono text-note text-u-text2">
          Until you set your own hours here, your booking link follows your Outlook working hours where Outlook states
          them, and the workspace's where it doesn't.
        </p>
      )}

      <section aria-label="Working hours" className="rounded-u-card border border-u-border bg-u-raised p-5">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="text-body font-semibold text-u-text">Working hours</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">
              Workspace default: {weekLabelOf(saved.workspaceWeeklyHours)}
            </div>
          </div>
          <span className="flex items-center gap-2 text-note text-u-text2">
            Use workspace hours
            <Toggle checked={usesWorkspaceHours} onChange={setUsesWorkspaceHours} label="Use workspace hours" />
          </span>
        </div>
        <div className="mt-2">
          <WeeklyHoursEditor
            value={usesWorkspaceHours ? draftOf(saved.workspaceWeeklyHours) : week}
            onChange={setWeek}
            disabled={usesWorkspaceHours}
          />
        </div>
      </section>

      <section aria-label="Daily email limit" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="text-body font-semibold text-u-text">Daily email limit</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">
              Workspace limit: {saved.workspaceDailyEmailCap} a day. You can set a lower one, for a new mailbox warming
              up.
            </div>
          </div>
          <span className="flex items-center gap-2 text-note text-u-text2">
            Use workspace limit
            <Toggle checked={usesWorkspaceCap} onChange={setUsesWorkspaceCap} label="Use workspace limit" />
          </span>
        </div>
        {!usesWorkspaceCap && (
          <label className="mt-3 flex flex-wrap items-center gap-2.5 font-mono text-note text-u-text3">
            <Input
              type="number"
              aria-label="My daily email limit"
              min={1}
              max={saved.workspaceDailyEmailCap}
              value={cap}
              onChange={(event) => setCap(event.target.value)}
              className="w-[96px] px-2.5 py-2 text-note"
            />
            emails a day · up to {saved.workspaceDailyEmailCap}
          </label>
        )}
        {capProblem && (
          <p role="alert" className="mt-2 text-note text-u-offlimits">
            {capProblem}
          </p>
        )}
      </section>

      <div className="mt-4 flex justify-end">
        <Button
          type="button"
          size="md"
          loading={save.isPending}
          disabled={weekProblem !== null || capProblem !== null}
          onClick={() => save.mutate()}
        >
          Save changes
        </Button>
      </div>
    </>
  );
}
