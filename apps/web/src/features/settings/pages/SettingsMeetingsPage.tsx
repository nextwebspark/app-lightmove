import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { MAILBOX_KEY } from "../../outreach/api/mailboxApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as availabilityApi from "../api/availabilityApi";
import type { WorkspaceAvailability } from "../api/availabilityApi";
import { WeeklyHoursEditor } from "../components/WeeklyHoursEditor";
import { draftOf, weekProblemOf, workingDaysOf } from "../lib/weeklyHours";

/**
 * Settings → Meetings & email (`Settings.dc.html`, `meetings`): the workspace's default working hours, which a booking
 * link offers times inside, the most each mailbox sends a day, and how far back a teammate's approach warns before
 * outreach. Admin only, like the rest of Workspace; each consultant can set their own hours and limit under
 * Availability.
 */
export function SettingsMeetingsPage() {
  const availability = useQuery({
    queryKey: availabilityApi.WORKSPACE_AVAILABILITY_KEY,
    queryFn: ({ signal }) => availabilityApi.getWorkspaceAvailability(signal),
  });
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });

  return (
    <>
      <PageHeader
        title="Meetings & email"
        subtitle="Your team's default working hours and sending limit. Each consultant can set their own."
      />
      {availability.isError || workspace.isError ? (
        <p className="text-note text-u-text3">{messageFor(availability.error ?? workspace.error)}</p>
      ) : availability.isPending || workspace.isPending ? (
        <CardsSkeleton />
      ) : (
        <MeetingsForm
          key={`${JSON.stringify(availability.data)}:${workspace.data.recentlyApproachedDays}`}
          saved={availability.data}
          savedApproachDays={workspace.data.recentlyApproachedDays}
        />
      )}
    </>
  );
}

const MAX_APPROACH_DAYS = 365;

function MeetingsForm({ saved, savedApproachDays }: { saved: WorkspaceAvailability; savedApproachDays: number }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [week, setWeek] = useState(() => draftOf(saved.weeklyHours));
  const [cap, setCap] = useState(String(saved.dailyEmailCap));
  const [approachDays, setApproachDays] = useState(String(savedApproachDays));

  const capNumber = Number(cap);
  const capProblem =
    !Number.isInteger(capNumber) || capNumber < 1 || capNumber > saved.maxDailyEmailCap
      ? `Choose a limit from 1 to ${saved.maxDailyEmailCap}.`
      : null;
  const approachDaysNumber = Number(approachDays);
  const approachProblem =
    approachDays.trim() === "" ||
    !Number.isInteger(approachDaysNumber) ||
    approachDaysNumber < 0 ||
    approachDaysNumber > MAX_APPROACH_DAYS
      ? `Choose from 0 to ${MAX_APPROACH_DAYS} days.`
      : null;

  const save = useMutation({
    mutationFn: async () => {
      const next = await availabilityApi.putWorkspaceAvailability({
        weeklyHours: workingDaysOf(week),
        dailyEmailCap: capNumber,
      });
      if (approachDaysNumber !== savedApproachDays) {
        queryClient.setQueryData(
          workspaceApi.WORKSPACE_KEY,
          await workspaceApi.changeRecentlyApproached(approachDaysNumber),
        );
      }
      return next;
    },
    onSuccess: (next) => {
      queryClient.setQueryData(availabilityApi.WORKSPACE_AVAILABILITY_KEY, next);
      void queryClient.invalidateQueries({ queryKey: availabilityApi.MY_AVAILABILITY_KEY });
      void queryClient.invalidateQueries({ queryKey: MAILBOX_KEY });
      toast("Meetings & email saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <>
      <section aria-label="Working hours" className="rounded-u-card border border-u-border bg-u-raised p-5">
        <div className="text-body font-semibold text-u-text">Working hours</div>
        <p className="mt-0.5 font-mono text-meta text-u-text3">
          Booking links offer times inside these hours, in each consultant's own time zone. Calendars still decide who
          is free, and anyone using Outlook follows their Outlook working hours.
        </p>
        {saved.suggestedFromCountry && (
          <p className="mt-3 inline-flex rounded-full bg-u-accent-tint px-2.5 py-0.5 font-mono text-meta text-u-text2">
            Suggested from {saved.suggestedFromCountry}
          </p>
        )}
        <div className="mt-2">
          <WeeklyHoursEditor value={week} onChange={setWeek} />
        </div>
      </section>

      <section aria-label="Daily email limit" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
        <div className="text-body font-semibold text-u-text">Daily email limit</div>
        <p className="mt-0.5 font-mono text-meta text-u-text3">
          The most each mailbox sends through Uncava in a day. A consultant can set a lower one; anything over waits
          for the next day.
        </p>
        <label className="mt-3 flex flex-wrap items-center gap-2.5 font-mono text-note text-u-text3">
          <Input
            type="number"
            aria-label="Daily email limit"
            min={1}
            max={saved.maxDailyEmailCap}
            value={cap}
            onChange={(event) => setCap(event.target.value)}
            className="w-[96px] px-2.5 py-2 text-note"
          />
          emails per mailbox per day · up to {saved.maxDailyEmailCap}
        </label>
        {capProblem && (
          <p role="alert" className="mt-2 text-note text-u-offlimits">
            {capProblem}
          </p>
        )}
      </section>

      <section aria-label="Recently approached" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
        <div className="text-body font-semibold text-u-text">Recently approached</div>
        <p className="mt-0.5 font-mono text-meta text-u-text3">
          Before an email or a sequence, Uncava says who on your team has approached the person — by email, a call,
          LinkedIn or WhatsApp — and for which position. It warns; it never stops the send.
        </p>
        <label className="mt-3 flex flex-wrap items-center gap-2.5 font-mono text-note text-u-text3">
          <Input
            type="number"
            aria-label="Recently approached window"
            min={0}
            max={MAX_APPROACH_DAYS}
            value={approachDays}
            onChange={(event) => setApproachDays(event.target.value)}
            className="w-[96px] px-2.5 py-2 text-note"
          />
          days · 0 turns it off · up to {MAX_APPROACH_DAYS}
        </label>
        {approachProblem && (
          <p role="alert" className="mt-2 text-note text-u-offlimits">
            {approachProblem}
          </p>
        )}
      </section>

      <div className="mt-4 flex justify-end">
        <Button
          type="button"
          size="md"
          loading={save.isPending}
          disabled={weekProblemOf(week) !== null || capProblem !== null || approachProblem !== null}
          onClick={() => save.mutate()}
        >
          Save changes
        </Button>
      </div>
    </>
  );
}
