import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, Input, Modal, Select, Skeleton, Toggle, useToast } from "../../../components/ui";
import { DateInput } from "../../../components/ui/DateInput";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import type { CandidateEmail, CandidateStatus } from "../../candidates/api/types";
import { statusById, useStatusAutomations, wouldMoveForward } from "../../workspace/lib/statusAutomation";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import * as meetingApi from "../api/meetingApi";
import type { Meeting, MeetingVideo } from "../api/meetingApi";
import * as notetakerApi from "../api/notetakerApi";
import type { WorkingHoursSource } from "../api/meetingApi";
import { meetingWhenOf, shiftDateOf, slotDayLabelOf } from "../lib/meetingTimes";
import { instantOf } from "../lib/sendSchedule";
import {
  choiceOfFields,
  fieldsOf,
  isWithinWorkingHours,
  refusalOf,
  viewDaysOf,
  type CallChoice,
} from "../lib/weekGrid";
import { SlotPager } from "./BookCallTimes";
import { WeekTimeGrid } from "./WeekTimeGrid";

const LENGTHS: { value: string; label: string }[] = [
  { value: "15", label: "15 min" },
  { value: "30", label: "30 min" },
  { value: "45", label: "45 min" },
  { value: "60", label: "1 hr" },
  { value: "90", label: "1.5 hr" },
  { value: "other", label: "Other" },
];

const WORKING_HOURS_LABEL: Record<WorkingHoursSource, string> = {
  CALENDAR: "Your working hours · Outlook",
  PERSONAL: "Your working hours",
  WORKSPACE: "Workspace working hours",
};

const VIDEO_LINKS: { value: MeetingVideo; label: string }[] = [
  { value: "GOOGLE_MEET", label: "Google Meet" },
  { value: "MICROSOFT_TEAMS", label: "Microsoft Teams" },
  { value: "ZOOM", label: "Zoom" },
  { value: "NONE", label: "No video link" },
];

/**
 * A calendar makes only its own provider's link — Meet from Google, Teams from Microsoft — and Zoom is offered only
 * where the consultant's own Zoom account is connected.
 */
function videoLinksFor(provider: string | undefined, isZoomOffered: boolean) {
  return VIDEO_LINKS.filter((option) => {
    if (option.value === "ZOOM") return isZoomOffered;
    if (option.value === "MICROSOFT_TEAMS") return provider !== "google";
    if (option.value === "GOOGLE_MEET") return provider !== "microsoft";
    return true;
  });
}

const DEFAULT_TITLE = "Confidential: first conversation";

interface NotetakerOutcome {
  sent: boolean;
  failure: unknown;
}

/**
 * Book a call (`Outreach.dc.html?dialog=bookCall`): any time on the consultant's own week, dragged on the calendar or
 * typed, and an invite from it. Their calendar's busy times are what refuse a time; working hours only label it.
 * Given a booked `meeting`, the same week moves that call instead: its link, address and title stay as booked.
 */
export function BookCallDialog({
  projectId,
  candidateId,
  fullName,
  candidateStatus,
  emails,
  meeting,
  notetakerOffered = false,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Only someone still Identified or Contacted moves to Engaged; the toast says so only then. */
  candidateStatus: CandidateStatus;
  emails: CandidateEmail[];
  /** The call to move, where this is a reschedule rather than a booking. */
  meeting?: Meeting;
  /** The Notetaker is set up and the person may be recorded: a booking offers to send it, switched on. */
  notetakerOffered?: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const firstName = fullName.trim().split(/\s+/)[0];
  const isRescheduling = meeting !== undefined;
  const [minutes, setMinutes] = useState(meeting ? minutesOf(meeting) : 30);
  const [start, setStart] = useState<string | null>(null);
  const [isOtherLength, setIsOtherLength] = useState(meeting !== undefined && !isPresetLength(minutesOf(meeting)));
  const [chosenVideo, setChosenVideo] = useState<MeetingVideo | null>(null);
  const [from, setFrom] = useState<string | null>(null);
  const [inviteAddress, setInviteAddress] = useState(emails[0]?.address ?? "");
  const [title, setTitle] = useState(DEFAULT_TITLE);
  const [withNotetaker, setWithNotetaker] = useState(true);

  const calendar = useQuery({
    queryKey: meetingApi.MEETING_CALENDAR_KEY(projectId, candidateId, from, meeting?.id ?? null),
    queryFn: ({ signal }) => meetingApi.getMeetingCalendar(projectId, candidateId, from, meeting?.id ?? null, signal),
    placeholderData: (previous) => previous,
  });
  const view = calendar.data;
  const timeZone = view?.timeZone ?? "UTC";
  const videoLinks = videoLinksFor(view?.provider, view?.zoomOffered === true);
  const video: MeetingVideo =
    chosenVideo !== null && videoLinks.some((option) => option.value === chosenVideo)
      ? chosenVideo
      : view?.provider === "microsoft"
        ? "MICROSOFT_TEAMS"
        : "GOOGLE_MEET";

  const choice: CallChoice | null = start ? { start, minutes } : null;
  const fields = choice ? fieldsOf(choice, timeZone) : null;
  const refusal =
    choice && view
      ? refusalOf(choice, {
          now: new Date(),
          minMinutes: view.minMinutes,
          maxMinutes: view.maxMinutes,
          latestDate: view.latestDate,
          timeZone,
          busy: view.busy,
        })
      : null;
  const isOutsideWorkingHours = choice !== null && view !== undefined && !isWithinWorkingHours(choice, view.workingHours);

  const { catalog } = usePipelineCatalog();
  const bookedRule = useStatusAutomations().data?.find((automation) => automation.trigger === "callBooked");
  const bookedTarget =
    bookedRule?.enabled && !bookedRule.broken ? statusById(catalog, bookedRule.statusId) : null;

  const offersNotetaker = !isRescheduling && notetakerOffered && video !== "NONE";
  const book = useMutation({
    mutationFn: async (): Promise<NotetakerOutcome> => {
      if (meeting) {
        await meetingApi.rescheduleMeeting(projectId, candidateId, meeting.id, { startsAt: start as string, minutes });
        return { sent: false, failure: null };
      }
      const booked = await meetingApi.bookMeeting(projectId, candidateId, {
        startsAt: start as string,
        minutes,
        video,
        inviteAddress,
        title: title.trim(),
      });
      if (!(offersNotetaker && withNotetaker)) return { sent: false, failure: null };
      // The invite has gone whatever the Notetaker answers, so its refusal is told apart rather than failing the booking.
      try {
        await notetakerApi.scheduleNotetaker(projectId, candidateId, booked.meetingId);
        return { sent: true, failure: null };
      } catch (failure) {
        return { sent: false, failure };
      }
    },
    onSuccess: (notetaker) => {
      const when = fields ? `${slotDayLabelOf(fields.date)}, ${fields.from}` : "";
      if (isRescheduling) {
        toast(`Call with ${firstName} moved to ${when}. They've been sent the new time.`);
      } else {
        const movesForward = bookedTarget !== null && wouldMoveForward(catalog, candidateStatus, bookedTarget.id);
        toast(
          `Invite sent to ${firstName} for ${when}.` +
            (movesForward ? ` They moved to ${bookedTarget.label}.` : "") +
            (notetaker.sent ? " The Notetaker will join and file its notes." : ""),
        );
      }
      if (notetaker.failure) {
        toast.error(
          codeOf(notetaker.failure) === "NOTETAKER_SEND_FAILED"
            ? "The Notetaker couldn't be sent. Switch it on from the call under Meetings to try again."
            : `The Notetaker wasn't sent: ${messageFor(notetaker.failure)}`,
        );
      }
      void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      onClose();
    },
    onError: (error) => {
      if (codeOf(error) === "MEETING_SLOT_TAKEN") {
        void calendar.refetch();
      }
      toast.error(messageFor(error));
      if (codeOf(error) === "MEETING_GONE") {
        void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
        onClose();
      }
    },
  });

  const handleChoose = (next: CallChoice) => {
    setStart(next.start);
    setMinutes(next.minutes);
    setIsOtherLength(!isPresetLength(next.minutes));
  };

  const handleLengthChange = (next: string) => {
    if (next === "other") {
      setIsOtherLength(true);
      return;
    }
    setIsOtherLength(false);
    setMinutes(Number(next));
  };

  const showDay = (day: string) => {
    if (!view) return;
    const days = viewDaysOf(view.from, view.days);
    if (!days.includes(day)) setFrom(day);
  };

  const handleDateChange = (day: string) => {
    if (!day) return;
    showDay(day);
    if (fields) setStart(instantOf(day, fields.from, timeZone).toISOString());
  };

  const handleFromChange = (time: string) => {
    if (!time || !view) return;
    setStart(instantOf(fields?.date ?? view.from, time, timeZone).toISOString());
  };

  const handleToChange = (time: string) => {
    if (!time || !fields) return;
    const next = choiceOfFields(fields.date, fields.from, time, timeZone);
    if (next) handleChoose(next);
  };

  const shownDays = view ? viewDaysOf(view.from, view.days) : [];
  const firstShown = shownDays[0];
  const lastShown = shownDays[shownDays.length - 1];
  const previousFrom =
    view && view.from > view.earliestDate
      ? [shiftDateOf(view.from, -view.days), view.earliestDate].sort().at(-1) ?? null
      : null;

  const summary =
    choice && fields ? `${slotDayLabelOf(fields.date)} · ${fields.from}–${fields.to} · ${timeZone}` : null;

  const footer = (
    <div className="flex w-full items-center gap-2.5">
      <div className="min-w-0 flex-1">
        {summary ? (
          <>
            <div className="truncate text-[13px] font-semibold text-u-text">{summary}</div>
            <div
              className={cn(
                "truncate font-mono text-[11.5px]",
                refusal ? "text-u-offlimits" : isOutsideWorkingHours ? "text-u-signal" : "text-u-text3",
              )}
              role={refusal ? "alert" : undefined}
            >
              {refusal ??
                (isOutsideWorkingHours
                  ? `Outside your working hours · ${minutes} min`
                  : `${minutes} min · ${isRescheduling ? "update" : "invite"} from ${view?.address}`)}
            </div>
          </>
        ) : (
          <span className="font-mono text-note text-u-text3">Drag on your calendar, or pick a time</span>
        )}
      </div>
      <Button type="button" variant="secondary" onClick={onClose}>
        Cancel
      </Button>
      <Button
        type="button"
        disabled={choice === null || refusal !== null || (!isRescheduling && (inviteAddress === "" || title.trim() === ""))}
        loading={book.isPending}
        onClick={() => book.mutate()}
      >
        {isRescheduling ? "Move call" : "Send invite"}
      </Button>
    </div>
  );

  return (
    <Modal
      open
      onClose={onClose}
      title={isRescheduling ? `Reschedule call with ${fullName}` : `Book a call with ${fullName}`}
      subtitle={view ? `Your calendar · ${view.address} · ${view.timeZone}` : " "}
      closeButton
      footer={footer}
      className="md:w-[1040px]"
    >
      <div className="grid gap-6 pb-2 md:grid-cols-[minmax(0,1fr)_280px]">
        <section aria-label="Time" className="min-w-0">
          <div className="mb-3 flex items-center gap-2">
            <span className="type-label text-u-text3">Week</span>
            <span className="hidden font-mono text-meta text-u-text3 sm:inline">Drag on your calendar to pick a time</span>
            {firstShown && lastShown && view && (
              <SlotPager
                firstShown={firstShown}
                lastShown={lastShown}
                earliestDate={view.earliestDate}
                latestDate={view.latestDate}
                previousFrom={previousFrom}
                isFetching={calendar.isFetching}
                onShowFrom={setFrom}
              />
            )}
          </div>

          {calendar.isPending ? (
            <Skeleton className="h-[420px] w-full" />
          ) : calendar.isError ? (
            <p role="alert" className="text-[12.5px] text-u-offlimits">
              {messageFor(calendar.error)}
            </p>
          ) : (
            view && (
              <div
                aria-busy={calendar.isPlaceholderData}
                className={cn("transition-opacity", calendar.isPlaceholderData && "pointer-events-none opacity-50")}
              >
                <WeekTimeGrid
                  days={shownDays}
                  timeZone={timeZone}
                  busy={view.busy}
                  workingHours={view.workingHours}
                  choice={choice}
                  clickMinutes={minutes}
                  minMinutes={view.minMinutes}
                  maxMinutes={view.maxMinutes}
                  now={new Date()}
                  onChoose={handleChoose}
                />
                <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 font-mono text-meta text-u-text3">
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm bg-u-accent-tint" />
                    {WORKING_HOURS_LABEL[view.workingHoursSource]}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm border border-u-border bg-u-raised" />
                    Busy
                  </span>
                </div>
                <fieldset className="mt-4 flex flex-wrap items-end gap-2.5">
                  <legend className="sr-only">Pick a time</legend>
                  <Field label="Pick a time">
                    <DateInput
                      value={fields?.date ?? firstShown ?? ""}
                      min={view.earliestDate}
                      max={view.latestDate}
                      onChange={handleDateChange}
                      ariaLabel="Call date"
                      className="w-[150px] px-2.5 py-2 text-note"
                    />
                  </Field>
                  <Field label="From">
                    <Input
                      type="time"
                      step={300}
                      aria-label="Call starts"
                      value={fields?.from ?? ""}
                      onChange={(event) => handleFromChange(event.target.value)}
                      className="w-[110px] px-2.5 py-2 text-note"
                    />
                  </Field>
                  <Field label="To">
                    <Input
                      type="time"
                      step={300}
                      aria-label="Call ends"
                      value={fields?.to === "24:00" ? "00:00" : (fields?.to ?? "")}
                      disabled={fields === null}
                      onChange={(event) => handleToChange(event.target.value)}
                      className="w-[110px] px-2.5 py-2 text-note"
                    />
                  </Field>
                </fieldset>
              </div>
            )
          )}
        </section>

        <section
          aria-label="Details"
          className="border-t border-u-border pt-5 md:border-s md:border-t-0 md:ps-6 md:pt-0"
        >
          <div className="mb-4">
            <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
              Length
            </span>
            <SegmentedControl
              label="Length"
              options={LENGTHS}
              value={isOtherLength ? "other" : String(minutes)}
              onChange={handleLengthChange}
              className="max-w-full flex-wrap"
            />
            {isOtherLength ? (
              <label className="mt-2 flex items-center gap-2 font-mono text-meta text-u-text3">
                <Input
                  type="number"
                  aria-label="Call length in minutes"
                  min={view?.minMinutes ?? 5}
                  max={view?.maxMinutes ?? 480}
                  step={5}
                  value={minutes}
                  onChange={(event) => setMinutes(Number(event.target.value))}
                  className="w-[84px] px-2.5 py-1.5 text-note"
                />
                minutes, up to {(view?.maxMinutes ?? 480) / 60} hr
              </label>
            ) : (
              <p className="mt-2 font-mono text-meta text-u-text3">Or drag on your calendar for any length.</p>
            )}
          </div>
          {meeting ? (
            <p className="text-note text-u-text3">
              Now {meetingWhenOf(meeting.startsAt, meeting.endsAt)}. {firstName} is sent the new time; the title
              {meeting.videoProvider ? ` and the ${meeting.videoProvider} link stay` : " stays"} as booked.
            </p>
          ) : (
            <>
              <Field
                label="Video"
                hint={
                  video === "MICROSOFT_TEAMS"
                    ? "Where your organisation doesn't offer Teams, the invite goes without a link."
                    : undefined
                }
              >
                <Select
                  aria-label="Video link"
                  value={video}
                  onChange={(event) => setChosenVideo(event.target.value as MeetingVideo)}
                  className="px-2.5 py-2 text-[12.5px]"
                >
                  {videoLinks.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Invite">
                <Select
                  aria-label="Invite address"
                  value={inviteAddress}
                  onChange={(event) => setInviteAddress(event.target.value)}
                  className="px-2.5 py-2 text-[12.5px]"
                >
                  {emails.map((email) => (
                    <option key={email.address} value={email.address}>
                      {email.kind ? `${email.address} · ${email.kind}` : email.address}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field
                label="Title"
                hint={`What ${firstName} sees. Keep the business unit out of it unless you mean to name it.`}
              >
                <Input
                  aria-label="Meeting title"
                  value={title}
                  maxLength={200}
                  onChange={(event) => setTitle(event.target.value)}
                  className="px-2.5 py-2 font-sans text-[13px]"
                />
              </Field>
              {offersNotetaker && (
                <div className="flex items-center gap-2.5 rounded-u-control border border-u-border bg-u-bg px-2.5 py-2">
                  <Icon
                    d={ICONS.mic}
                    size={14}
                    className={cn("flex-none", withNotetaker ? "text-u-accent" : "text-u-text3")}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-note font-medium text-u-text">Record with Notetaker</span>
                    <span className="block font-mono text-meta text-u-text3">
                      {withNotetaker ? `Joins 1 min early · files notes on ${firstName}` : "Off · nothing is recorded"}
                    </span>
                  </span>
                  <Toggle checked={withNotetaker} onChange={setWithNotetaker} label="Record with Notetaker" />
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </Modal>
  );
}

function isPresetLength(minutes: number): boolean {
  return LENGTHS.some((option) => option.value === String(minutes));
}

function minutesOf(meeting: Meeting): number {
  return Math.round((Date.parse(meeting.endsAt) - Date.parse(meeting.startsAt)) / 60_000);
}
