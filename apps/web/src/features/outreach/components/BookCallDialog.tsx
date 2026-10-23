import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, Input, Modal, Select, Skeleton, Toggle, useToast } from "../../../components/ui";
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
import { meetingWhenOf, slotDayLabelOf, slotTimeOf } from "../lib/meetingTimes";
import { SlotDayTiles, SlotGroup, SlotPager } from "./BookCallTimes";

type Length = "15" | "30" | "45" | "60" | "90";

const LENGTHS: { value: Length; label: string }[] = [
  { value: "15", label: "15 min" },
  { value: "30", label: "30 min" },
  { value: "45", label: "45 min" },
  { value: "60", label: "1 hr" },
  { value: "90", label: "1.5 hr" },
];

const OFFERED_LENGTHS = new Intl.ListFormat("en", { type: "disjunction" }).format(LENGTHS.map((option) => option.label));

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
 * Book a call (`Outreach.dc.html?dialog=bookCall`): a free time off the consultant's own calendar, and an invite from it.
 * Given a booked `meeting`, the same grid moves that call instead: its link, address and title stay as booked.
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
  const [length, setLength] = useState<Length>(meeting ? lengthOf(meeting) : "30");
  const [chosenVideo, setChosenVideo] = useState<MeetingVideo | null>(null);
  const [slot, setSlot] = useState<string | null>(null);
  const [from, setFrom] = useState<string | null>(null);
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [inviteAddress, setInviteAddress] = useState(emails[0]?.address ?? "");
  const [title, setTitle] = useState(DEFAULT_TITLE);
  const [withNotetaker, setWithNotetaker] = useState(true);

  const minutes = Number(length);
  const slots = useQuery({
    queryKey: meetingApi.MEETING_SLOTS_KEY(projectId, candidateId, minutes, from, meeting?.id ?? null),
    queryFn: ({ signal }) =>
      meetingApi.getMeetingSlots(projectId, candidateId, minutes, from, meeting?.id ?? null, signal),
    placeholderData: (previous) => previous,
  });
  const timeZone = slots.data?.timeZone;
  const videoLinks = videoLinksFor(slots.data?.provider, slots.data?.zoomOffered === true);
  const video: MeetingVideo =
    chosenVideo !== null && videoLinks.some((option) => option.value === chosenVideo)
      ? chosenVideo
      : slots.data?.provider === "microsoft"
        ? "MICROSOFT_TEAMS"
        : "GOOGLE_MEET";
  const isSlotOffered = slot !== null && (slots.data?.days.some((day) => day.starts.includes(slot)) ?? false);

  const { catalog } = usePipelineCatalog();
  const bookedRule = useStatusAutomations().data?.find((automation) => automation.trigger === "callBooked");
  const bookedTarget =
    bookedRule?.enabled && !bookedRule.broken ? statusById(catalog, bookedRule.statusId) : null;

  const offersNotetaker = !isRescheduling && notetakerOffered && video !== "NONE";
  const book = useMutation({
    mutationFn: async (): Promise<NotetakerOutcome> => {
      if (meeting) {
        await meetingApi.rescheduleMeeting(projectId, candidateId, meeting.id, { startsAt: slot as string, minutes });
        return { sent: false, failure: null };
      }
      const booked = await meetingApi.bookMeeting(projectId, candidateId, {
        startsAt: slot as string,
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
      const day = slots.data?.days.find((candidate) => candidate.starts.includes(slot as string));
      const time = slotTimeOf(slot as string, timeZone ?? "UTC");
      const when = day ? `${slotDayLabelOf(day.date)}, ${time}` : time;
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
        setSlot(null);
        void slots.refetch();
      }
      toast.error(messageFor(error));
      if (codeOf(error) === "MEETING_GONE") {
        void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
        onClose();
      }
    },
  });

  const handleLengthChange = (next: Length) => {
    setLength(next);
    setSlot(null);
  };

  const handleShowFrom = (day: string) => {
    setFrom(day);
    setChosenDate(null);
    setSlot(null);
  };

  const shownDays = slots.data?.days ?? [];
  const firstShown = shownDays[0]?.date;
  const lastShown = shownDays[shownDays.length - 1]?.date;
  const earliestDate = slots.data?.earliestDate;
  const latestDate = slots.data?.latestDate;
  const activeDay =
    shownDays.find((day) => day.date === chosenDate) ??
    shownDays.find((day) => day.starts.length > 0) ??
    shownDays[0];
  const morning = activeDay && timeZone ? activeDay.starts.filter((start) => slotTimeOf(start, timeZone) < "12:00") : [];
  const afternoon = activeDay && timeZone ? activeDay.starts.filter((start) => slotTimeOf(start, timeZone) >= "12:00") : [];

  const chosenDay = slots.data?.days.find((day) => slot !== null && day.starts.includes(slot));
  const summary =
    slot && chosenDay && timeZone
      ? `${slotDayLabelOf(chosenDay.date)} · ${slotTimeOf(slot, timeZone)}–${slotTimeOf(endOf(slot, minutes), timeZone)} · ${timeZone}`
      : null;

  const footer = (
    <div className="flex w-full items-center gap-2.5">
      <div className="min-w-0 flex-1">
        {summary ? (
          <>
            <div className="truncate text-[13px] font-semibold text-u-text">{summary}</div>
            <div className="truncate font-mono text-[11.5px] text-u-text3">
              {minutes} min · {isRescheduling ? "update" : "invite"} from {slots.data?.address}
            </div>
          </>
        ) : (
          <span className="font-mono text-[12px] text-u-text3">Pick a day and a time</span>
        )}
      </div>
      <Button type="button" variant="secondary" onClick={onClose}>
        Cancel
      </Button>
      <Button
        type="button"
        disabled={!isSlotOffered || (!isRescheduling && (inviteAddress === "" || title.trim() === ""))}
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
      subtitle={slots.data ? `Free times on ${slots.data.address} · ${slots.data.timeZone}` : " "}
      closeButton
      footer={footer}
      className="md:w-[940px]"
    >
      <div className="grid gap-6 pb-2 md:grid-cols-[minmax(0,1fr)_280px]">
        <section aria-label="Time" className="min-w-0">
          <div className="mb-3 flex items-center gap-2">
            <span className="type-label text-u-text3">Day</span>
            {firstShown && lastShown && earliestDate && latestDate && (
              <SlotPager
                firstShown={firstShown}
                lastShown={lastShown}
                earliestDate={earliestDate}
                latestDate={latestDate}
                previousFrom={slots.data?.previousFrom ?? null}
                isFetching={slots.isFetching}
                onShowFrom={handleShowFrom}
              />
            )}
          </div>

          {slots.isPending ? (
            <Skeleton className="h-[300px] w-full" />
          ) : slots.isError ? (
            <p role="alert" className="text-[12.5px] text-u-offlimits">
              {messageFor(slots.error)}
            </p>
          ) : (
            <div
              aria-busy={slots.isPlaceholderData}
              className={cn("transition-opacity", slots.isPlaceholderData && "pointer-events-none opacity-50")}
            >
              <SlotDayTiles days={shownDays} activeDate={activeDay?.date} onChoose={setChosenDate} />

              <div className="min-h-[220px]">
                {activeDay && activeDay.starts.length === 0 ? (
                  <div className="grid h-[220px] place-items-center rounded-[8px] border border-dashed border-u-border text-center">
                    <div>
                      <div className="text-[13px] text-u-text2">No free time on {slotDayLabelOf(activeDay.date)}</div>
                      <div className="mt-1 font-mono text-[11.5px] text-u-text3">Pick another day, or page ahead.</div>
                    </div>
                  </div>
                ) : (
                  <>
                    <SlotGroup label="Morning" starts={morning} timeZone={timeZone} chosen={slot} onChoose={setSlot} />
                    <SlotGroup label="Afternoon" starts={afternoon} timeZone={timeZone} chosen={slot} onChoose={setSlot} />
                  </>
                )}
              </div>
            </div>
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
              value={length}
              onChange={handleLengthChange}
              className="max-w-full flex-wrap"
            />
          </div>
          {meeting ? (
            <p className="text-note text-u-text3">
              Now {meetingWhenOf(meeting.startsAt, meeting.endsAt)}. {firstName} is sent the new time; the title
              {meeting.videoProvider ? ` and the ${meeting.videoProvider} link stay` : " stays"} as booked.
              {!isOfferedLength(meeting) && ` It was booked for ${minutesOf(meeting)} min; a moved call is ${OFFERED_LENGTHS}.`}
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

/** The call's own length where the dialog offers it; a call of any other length starts on 30, and the note says so. */
function lengthOf(meeting: Meeting): Length {
  return isOfferedLength(meeting) ? (String(minutesOf(meeting)) as Length) : "30";
}

function isOfferedLength(meeting: Meeting): boolean {
  return LENGTHS.some((option) => option.value === String(minutesOf(meeting)));
}

function minutesOf(meeting: Meeting): number {
  return Math.round((Date.parse(meeting.endsAt) - Date.parse(meeting.startsAt)) / 60_000);
}

function endOf(start: string, minutes: number): string {
  return new Date(Date.parse(start) + minutes * 60_000).toISOString();
}
