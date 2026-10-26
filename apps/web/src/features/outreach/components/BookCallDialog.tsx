import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input, Modal, Select, Skeleton, useToast } from "../../../components/ui";
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
  // The day chosen in Pick a time before any time is: the time typed next lands on it, never on the week's first day.
  const [pickedDate, setPickedDate] = useState<string | null>(null);
  const [inviteAddress, setInviteAddress] = useState(emails[0]?.address ?? "");
  const [title, setTitle] = useState(DEFAULT_TITLE);
  // Focus opens on the week's heading: landing in the invite's first field reads as something to fix.
  const weekHeading = useRef<HTMLHeadingElement>(null);

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

  const book = useMutation({
    mutationFn: async () => {
      if (meeting) {
        await meetingApi.rescheduleMeeting(projectId, candidateId, meeting.id, { startsAt: start as string, minutes });
        return;
      }
      await meetingApi.bookMeeting(projectId, candidateId, {
        startsAt: start as string,
        minutes,
        video,
        inviteAddress,
        title: title.trim(),
      });
    },
    onSuccess: () => {
      const when = fields ? `${slotDayLabelOf(fields.date)}, ${fields.from}` : "";
      if (isRescheduling) {
        toast(`Call with ${firstName} moved to ${when}. They've been sent the new time.`);
      } else {
        const movesForward = bookedTarget !== null && wouldMoveForward(catalog, candidateStatus, bookedTarget.id);
        toast(
          `Invite sent to ${firstName} for ${when}.` + (movesForward ? ` They moved to ${bookedTarget.label}.` : ""),
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

  /** A picked time the new week does not show is dropped, so Send invite never books a day off the screen. */
  const handleShowWeek = (day: string) => {
    setFrom(day);
    const shown = viewDaysOf(day, view?.days ?? 7);
    if (fields && !shown.includes(fields.date)) setStart(null);
    if (pickedDate && !shown.includes(pickedDate)) setPickedDate(null);
  };

  const handleDateChange = (day: string) => {
    if (!day) return;
    showDay(day);
    setPickedDate(day);
    if (fields) setStart(instantOf(day, fields.from, timeZone).toISOString());
  };

  const handleFromChange = (time: string) => {
    if (!time || !view) return;
    setStart(instantOf(fields?.date ?? pickedDate ?? view.from, time, timeZone).toISOString());
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

  const whenLine = fields ? `${fields.from} – ${fields.to === "24:00" ? "00:00" : fields.to} · ${lengthLabelOf(minutes)}` : null;
  const missing = isRescheduling
    ? null
    : inviteAddress === ""
      ? `Add an email for ${firstName} to send an invite`
      : title.trim() === ""
        ? "Give the call a title"
        : null;
  const status: { tone: "refused" | "warn" | "ok"; words: string } | null = choice
    ? refusal
      ? { tone: "refused", words: refusal }
      : missing
        ? { tone: "warn", words: missing }
        : isOutsideWorkingHours
          ? { tone: "warn", words: "Outside your working hours" }
          : { tone: "ok", words: "Free on your calendar" }
    : null;

  const footer = (
    <div className="flex w-full flex-wrap items-center gap-x-4 gap-y-3">
      {view && (
        <fieldset className="grid w-full grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-1.5 sm:flex sm:w-auto">
          <legend className="sr-only">Pick a time</legend>
          <span aria-hidden="true" className="me-1 hidden type-micro-label font-mono text-u-text3 sm:inline">
            Pick a time
          </span>
          <DateInput
            value={fields?.date ?? pickedDate ?? firstShown ?? ""}
            min={view.earliestDate}
            max={view.latestDate}
            onChange={handleDateChange}
            ariaLabel="Call date"
            className="col-span-3 bg-u-bg px-2.5 py-1.5 text-note sm:w-[140px]"
          />
          <Input
            type="time"
            step={300}
            aria-label="Call starts"
            value={fields?.from ?? ""}
            invalid={refusal !== null}
            onChange={(event) => handleFromChange(event.target.value)}
            className="w-full bg-u-bg px-2.5 py-1.5 text-note sm:w-[104px]"
          />
          <span aria-hidden="true" className="text-note text-u-text3">
            –
          </span>
          <Input
            type="time"
            step={300}
            aria-label="Call ends"
            value={fields?.to === "24:00" ? "00:00" : (fields?.to ?? "")}
            invalid={refusal !== null}
            disabled={fields === null}
            onChange={(event) => handleToChange(event.target.value)}
            className="w-full bg-u-bg px-2.5 py-1.5 text-note sm:w-[104px]"
          />
        </fieldset>
      )}
      <div className="min-w-0 flex-1">
        {fields && whenLine ? (
          <>
            <div className="flex flex-wrap items-baseline gap-x-2">
              <span className="text-body font-semibold text-u-text">{slotDayLabelOf(fields.date)}</span>
              <span className="text-note text-u-text2">{whenLine}</span>
            </div>
            {status && (
              <div
                role={status.tone === "refused" ? "alert" : undefined}
                className={cn(
                  "mt-0.5 flex items-center gap-1.5 text-note font-medium",
                  status.tone === "refused"
                    ? "text-u-offlimits"
                    : status.tone === "warn"
                      ? "text-u-signal"
                      : "text-u-direct",
                )}
              >
                <span aria-hidden="true" className="size-1.5 flex-none rounded-full bg-current" />
                {status.words}
              </div>
            )}
          </>
        ) : (
          view && (
            <div className="flex items-center gap-2 text-note text-u-text3">
              <Icon d={ICONS.calendar} size={14} className="flex-none" />
              Drag on your calendar, or type a time
            </div>
          )
        )}
      </div>
      <div className="ms-auto flex flex-none items-center gap-2.5">
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button
          type="button"
          disabled={choice === null || refusal !== null || missing !== null}
          loading={book.isPending}
          onClick={() => book.mutate()}
        >
          {isRescheduling ? "Move call" : "Send invite"}
        </Button>
      </div>
    </div>
  );

  return (
    <Modal
      open
      onClose={onClose}
      title={isRescheduling ? `Reschedule call with ${fullName}` : `Book a call with ${fullName}`}
      subtitle={view ? `Your calendar · ${view.address} · ${view.timeZone}` : " "}
      closeButton
      initialFocusRef={weekHeading}
      footer={footer}
      className="md:h-[min(90dvh,860px)] md:w-[1080px]"
    >
      <div className="grid gap-6 pb-2 md:h-full md:grid-cols-[minmax(0,1fr)_300px] md:grid-rows-[minmax(0,1fr)]">
        <section aria-label="Time" className="flex min-w-0 flex-col md:min-h-0">
          <div className="mb-3 flex flex-none items-center gap-2">
            <h3 ref={weekHeading} tabIndex={-1} className="text-lead font-semibold text-u-text outline-none">{firstShown ? monthOf(firstShown) : "\u00a0"}</h3>
            {firstShown && lastShown && view && (
              <SlotPager
                firstShown={firstShown}
                lastShown={lastShown}
                earliestDate={view.earliestDate}
                latestDate={view.latestDate}
                previousFrom={previousFrom}
                isFetching={calendar.isFetching}
                onShowFrom={handleShowWeek}
              />
            )}
          </div>

          {calendar.isPending ? (
            <Skeleton className="h-[540px] w-full md:h-auto md:min-h-0 md:flex-1" />
          ) : calendar.isError ? (
            <p role="alert" className="text-body text-u-offlimits">
              {messageFor(calendar.error)}
            </p>
          ) : (
            view && (
              <div
                aria-busy={calendar.isPlaceholderData}
                className={cn(
                  "flex flex-col transition-opacity md:min-h-0 md:flex-1",
                  calendar.isPlaceholderData && "pointer-events-none opacity-50",
                )}
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
                  className="md:max-h-none md:min-h-0 md:flex-1"
                />
                <div className="mt-2.5 flex flex-none flex-wrap items-center gap-x-4 gap-y-1 text-meta text-u-text3">
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="size-2.5 rounded-sm bg-u-accent-tint ring-1 ring-u-accent/30" />
                    {WORKING_HOURS_LABEL[view.workingHoursSource]}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="size-2.5 rounded-sm border border-u-border-strong bg-u-raised" />
                    Busy
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="h-0.5 w-2.5 rounded-full bg-u-offlimits" />
                    Now
                  </span>
                  <span className="ms-auto hidden sm:inline">Drag to pick a time · click for {lengthLabelOf(minutes)}</span>
                </div>
              </div>
            )
          )}
        </section>

        <section
          aria-label="Details"
          className={cn(
            "flex flex-col gap-5 border-t border-u-border pt-5",
            "md:min-h-0 md:overflow-y-auto md:border-s md:border-t-0 md:pb-1 md:pe-1 md:ps-6 md:pt-0",
            "[scrollbar-color:var(--color-u-border-strong)_transparent] [scrollbar-width:thin]",
          )}
        >
          <div>
            <SectionLabel>Length</SectionLabel>
            <SegmentedControl
              label="Length"
              options={LENGTHS}
              value={isOtherLength ? "other" : String(minutes)}
              onChange={handleLengthChange}
              className="grid w-full grid-cols-3"
            />
            {isOtherLength && (
              <label className="mt-2 flex items-center gap-2 text-note text-u-text3">
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
            )}
          </div>

          {meeting ? (
            <p className="rounded-u-chip bg-u-surface px-3 py-2.5 text-note text-u-text2">
              Now {meetingWhenOf(meeting.startsAt, meeting.endsAt)}. {firstName} is sent the new time; the title
              {meeting.videoProvider ? ` and the ${meeting.videoProvider} link stay` : " stays"} as booked.
            </p>
          ) : (
            <>
              <div>
                <SectionLabel>Invite</SectionLabel>
                <div className="flex flex-col gap-2">
                  <WithIcon icon={ICONS.mail}>
                    <Select
                      aria-label="Invite address"
                      value={inviteAddress}
                      onChange={(event) => setInviteAddress(event.target.value)}
                      className="py-2 ps-8 pe-2.5 text-body"
                    >
                    {emails.map((email) => (
                      <option key={email.address} value={email.address}>
                            {email.kind ? `${email.address} · ${email.kind}` : email.address}
                      </option>
                    ))}
                    </Select>
                  </WithIcon>
                  <WithIcon icon={ICONS.pencil}>
                    <Input
                      aria-label="Meeting title"
                      value={title}
                      maxLength={200}
                      onChange={(event) => setTitle(event.target.value)}
                      className="py-2 ps-8 pe-2.5 font-sans text-body"
                    />
                  </WithIcon>
                  <WithIcon icon={ICONS.video}>
                    <Select
                      aria-label="Video link"
                      value={video}
                      onChange={(event) => setChosenVideo(event.target.value as MeetingVideo)}
                      className="py-2 ps-8 pe-2.5 text-body"
                    >
                      {videoLinks.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </Select>
                  </WithIcon>
                  <p className="text-meta text-u-text3">{firstName} sees the title. Keep the business unit out of it.</p>
                  {video === "MICROSOFT_TEAMS" && (
                    <p className="text-meta text-u-text3">
                      Where your organisation doesn&apos;t offer Teams, the invite goes without a link.
                    </p>
                  )}
                </div>
              </div>
            </>
          )}
        </section>
      </div>
    </Modal>
  );
}

function SectionLabel({ children }: { children: ReactNode }) {
  return <h3 className="mb-2 type-micro-label font-mono text-u-text3">{children}</h3>;
}

function WithIcon({ icon, children }: { icon: string; children: ReactNode }) {
  return (
    <div className="relative">
      {children}
      <Icon
        d={icon}
        size={14}
        className="pointer-events-none absolute start-2.5 top-1/2 -translate-y-1/2 text-u-text3"
      />
    </div>
  );
}

function lengthLabelOf(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = minutes / 60;
  return Number.isInteger(hours) ? `${hours} hr` : `${minutes} min`;
}

function monthOf(isoDate: string): string {
  const [year, month] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, 1)).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

function isPresetLength(minutes: number): boolean {
  return LENGTHS.some((option) => option.value === String(minutes));
}

function minutesOf(meeting: Meeting): number {
  return Math.round((Date.parse(meeting.endsAt) - Date.parse(meeting.startsAt)) / 60_000);
}
