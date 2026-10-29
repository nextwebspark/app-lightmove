import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Button, Field, SegmentedControl, Select, useToast } from "../../../components/ui";
import {
  STANDARD_DATE_FORMAT,
  STANDARD_TIME_FORMAT,
  type DateFormatStyle,
  type TimeFormatStyle,
} from "../../../lib/datetime";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as mailboxApi from "../../outreach/api/mailboxApi";
import type { WorkspaceDateTime } from "../../workspace/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import {
  dateFormatSelectOptions,
  timeFormatSelectOptions,
  timeZoneSelectOptions,
} from "../lib/dateTimeOptions";
import { DateTimePreview } from "./DateTimePreview";

/**
 * Settings → General's Date & time: the zone and formats everyone in the workspace reads in, until they choose their
 * own in Settings → Profile. Every field opens on a value — the country's zone, the standard formats — and a value
 * equal to that default is saved as "not chosen", so a later change of the firm's country still reaches it.
 */
export function WorkspaceDateTimeCard({
  workspaceName,
  memberCount,
  dateTime,
}: {
  workspaceName: string;
  memberCount: number;
  dateTime: WorkspaceDateTime;
}) {
  const { reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();

  const saved = {
    timeZone: dateTime.timeZone ?? dateTime.suggestedTimeZone,
    dateFormat: dateTime.dateFormat ?? STANDARD_DATE_FORMAT,
    timeFormat: dateTime.timeFormat ?? STANDARD_TIME_FORMAT,
  };
  const [timeZone, setTimeZone] = useState(saved.timeZone);
  const [dateFormat, setDateFormat] = useState<DateFormatStyle>(saved.dateFormat);
  const [timeFormat, setTimeFormat] = useState<TimeFormatStyle>(saved.timeFormat);

  const zoneOptions = useMemo(() => timeZoneSelectOptions(dateTime.timeZone), [dateTime.timeZone]);
  const dateOptions = useMemo(() => dateFormatSelectOptions(), []);
  const timeOptions = useMemo(() => timeFormatSelectOptions(), []);

  const isDirty =
    timeZone !== saved.timeZone || dateFormat !== saved.dateFormat || timeFormat !== saved.timeFormat;
  const isStandard =
    timeZone === dateTime.suggestedTimeZone &&
    dateFormat === STANDARD_DATE_FORMAT &&
    timeFormat === STANDARD_TIME_FORMAT;
  const ownChoices = memberCount - dateTime.membersFollowing;

  const save = useMutation({
    mutationFn: () =>
      workspaceApi.updateDateTime({
        timeZone: timeZone === dateTime.suggestedTimeZone ? null : timeZone,
        dateFormat: dateFormat === STANDARD_DATE_FORMAT ? null : dateFormat,
        timeFormat: timeFormat === STANDARD_TIME_FORMAT ? null : timeFormat,
      }),
    onSuccess: async (workspace) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, workspace);
      // A follower's mailbox sends by the new zone, and the auth summary carries the admin's own clock.
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
      await reload();
      toast("Date and time saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleReset = () => {
    setTimeZone(dateTime.suggestedTimeZone);
    setDateFormat(STANDARD_DATE_FORMAT);
    setTimeFormat(STANDARD_TIME_FORMAT);
  };

  return (
    <section aria-labelledby="workspace-date-time" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex-1">
          <h2 id="workspace-date-time" className="text-sm font-semibold">
            Date &amp; time
          </h2>
          <p className="mt-0.5 font-mono text-meta text-u-text3">
            How dates and times read for everyone in {workspaceName}. Members can choose their own in Settings →
            Profile.
          </p>
        </div>
        {!isStandard && (
          <button
            type="button"
            onClick={handleReset}
            className="flex-none text-note font-medium text-u-accent hover:underline"
          >
            Reset to standard
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
        <Field
          label="Time zone"
          hint={
            timeZone === dateTime.suggestedTimeZone && dateTime.suggestedFrom
              ? `From ${dateTime.suggestedFrom}`
              : undefined
          }
        >
          <Select value={timeZone} className="bg-u-surface" onChange={(event) => setTimeZone(event.target.value)}>
            {zoneOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Date format">
          <Select value={dateFormat} className="bg-u-surface" onChange={(event) => setDateFormat(event.target.value as DateFormatStyle)}>
            {dateOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <div className="mb-4">
          <span className="mb-1.5 block type-micro-label font-mono text-u-text3">Time format</span>
          <SegmentedControl label="Time format" options={timeOptions} value={timeFormat} onChange={setTimeFormat} />
        </div>
      </div>

      <DateTimePreview
        preferences={{ timeZone, dateFormat, timeFormat }}
        note={
          ownChoices > 0
            ? `Applies to ${dateTime.membersFollowing} of ${memberCount} members · ${ownChoices} chose their own`
            : "Applies to everyone in the workspace"
        }
      />

      <p className="mt-3 text-meta text-u-text3">
        The time zone also sets when sequences send and when daily digests arrive for members who follow the
        workspace.
      </p>

      <div className="mt-5 flex justify-end">
        <Button loading={save.isPending} disabled={!isDirty} onClick={() => save.mutate()}>
          Save date &amp; time
        </Button>
      </div>
    </section>
  );
}
