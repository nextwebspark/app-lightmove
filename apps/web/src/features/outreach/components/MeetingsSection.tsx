import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateEmail, CandidateStatus } from "../../candidates/api/types";
import * as meetingApi from "../api/meetingApi";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import { dateTileOf, meetingWhenOf, shortDateOf } from "../lib/meetingTimes";
import { openCopilotWindow } from "../lib/copilotWindow";
import { canRecord, hasCallEnded, isNotetakerLive, recordingOfMeeting } from "../lib/notetakerChip";
import { useMailbox } from "../lib/useMailbox";
import { useNotetaker } from "../lib/useNotetaker";
import { BookCallDialog } from "./BookCallDialog";
import { CallAssistantPanel } from "./CallAssistantPanel";
import { CallChangesDialog } from "./CallChangesDialog";
import { CallMenu } from "./CallMenu";
import { InterviewPlanDialog } from "./copilot/InterviewPlanDialog";
import { TranscriptSheet } from "./TranscriptSheet";
import { ZoomConnectControl } from "./ZoomConnectControl";

/**
 * The executive drawer's Meetings section (`Outreach.dc.html?drawer=`): calls with them on the team's
 * connected calendars, and Book a call from the consultant's own. Always open, unlike the folds below
 * it. Staff-only; Book a call is never offered for someone marked do not contact. A call's row is the call and
 * its Join link; everything else is behind its ⋯ — Notetaker & questions, what came of the call once its notes are
 * filed, and Reschedule and Cancel for a call booked in Uncava on the consultant's own calendar (`editable`, the
 * server's say). A call the Notetaker has left is past.
 */
export function MeetingsSection({
  projectId,
  candidateId,
  personId,
  fullName,
  candidateStatus,
  emails,
  onShowCopilot,
}: {
  projectId: string;
  candidateId: string;
  personId: string;
  fullName: string;
  candidateStatus: CandidateStatus;
  emails: CandidateEmail[];
  /** Brings a call's copilot up in the drawer's Copilot tab. */
  onShowCopilot: (recordingId: string) => void;
}) {
  const meetings = useQuery({
    queryKey: meetingApi.MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => meetingApi.getMeetings(projectId, candidateId, signal),
  });
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(personId),
    queryFn: ({ signal }) => poolApi.getPerson(personId, signal),
  });
  const notetaker = useNotetaker(projectId, candidateId);
  const mailbox = useMailbox();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isBooking, setIsBooking] = useState(false);
  const [rescheduling, setRescheduling] = useState<Meeting | null>(null);
  const [cancelling, setCancelling] = useState<Meeting | null>(null);
  const [transcriptOf, setTranscriptOf] = useState<string | null>(null);
  const [suggestionsOf, setSuggestionsOf] = useState<string | null>(null);
  const [planOf, setPlanOf] = useState<string | null>(null);
  const [panelOf, setPanelOf] = useState<string | null>(null);

  const firstName = fullName.trim().split(/\s+/)[0];
  const recordings = notetaker.data?.recordings ?? [];
  const recordingOf = (meeting: Meeting): NotetakerRecording | null => recordingOfMeeting(recordings, meeting);
  const booked = meetings.data?.upcoming ?? [];
  const upcoming = booked.filter((meeting) => !hasCallEnded(recordingOf(meeting)));
  const past = [...booked.filter((meeting) => hasCallEnded(recordingOf(meeting))), ...(meetings.data?.past ?? [])];
  const count = upcoming.length + past.length;
  const hasActiveMailbox = mailbox.data?.offered === true && mailbox.data.connection?.status === "ACTIVE";
  const canBook = hasActiveMailbox && record.isSuccess && !record.data.doNotContact && emails.length > 0;
  const canReschedule = hasActiveMailbox && record.isSuccess && !record.data.doNotContact;
  const recordable = new Set(notetaker.data?.recordableMeetingIds ?? []);
  const startable = new Set(notetaker.data?.startableMeetingIds ?? []);
  const isRecordable = (meeting: Meeting) =>
    notetaker.data?.offered === true && record.isSuccess && !record.data.doNotContact && recordable.has(meeting.id);
  const mayRecord = (meeting: Meeting) => isRecordable(meeting) && canRecord(recordingOf(meeting));
  const offersNotetaker = (meeting: Meeting) => mayRecord(meeting) || isNotetakerLive(recordingOf(meeting));
  const panelMeeting = [...upcoming, ...past].find((meeting) => meeting.id === panelOf) ?? null;
  const menuOf = (
    meeting: Meeting,
    options?: { label: string; icon: string; onSelect: () => void },
    onReschedule?: () => void,
    onCancel?: () => void,
  ) => (
    <CallMenu
      projectId={projectId}
      candidateId={candidateId}
      firstName={firstName}
      recording={recordingOf(meeting)}
      options={options}
      onShowTranscript={setTranscriptOf}
      onShowChanges={setSuggestionsOf}
      onOpenCopilot={handleOpenCopilot}
      onReschedule={onReschedule}
      onCancel={onCancel}
    />
  );

  // Straight from the click: the floating window needs the gesture. Where the browser opens no window, the drawer's
  // Copilot tab shows it.
  function handleOpenCopilot(recordingId: string) {
    const showInDrawer = () => onShowCopilot(recordingId);
    void openCopilotWindow({ projectId, candidateId, recordingId }, showInDrawer).then((opened) => {
      if (opened === "blocked") showInDrawer();
    });
  }

  const cancel = useMutation({
    mutationFn: (meeting: Meeting) => meetingApi.cancelMeeting(projectId, candidateId, meeting.id),
    onSuccess: () => {
      toast(`Call with ${firstName} cancelled. They've been told.`);
      void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      setCancelling(null);
    },
    onError: (error) => {
      toast.error(messageFor(error));
      if (codeOf(error) === "MEETING_GONE") {
        void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
        setCancelling(null);
      }
    },
  });

  return (
    <section aria-label="Meetings" className="border-b border-u-border py-3.5">
      <div className="mb-2.5 flex items-center gap-2">
        <Icon d={ICONS.calendar} size={14} className="text-u-text3" />
        <span className="text-[13px] font-semibold">Meetings</span>
        {count > 0 && <span className="font-mono text-[11px] font-medium text-u-text3">{count}</span>}
        {canBook && (
          <button
            type="button"
            onClick={() => setIsBooking(true)}
            className="ms-auto inline-flex items-center gap-1.5 rounded-[6px] border border-u-accent-solid bg-u-accent-solid px-[11px] py-[5px] text-[12px] font-semibold text-white hover:bg-u-accent-solid-hover"
          >
            <Icon d={ICONS.plus} size={12} />
            Book a call
          </button>
        )}
      </div>

      {meetings.isError ? (
        <p className="text-[12.5px] text-u-text3">Meetings couldn't be loaded.</p>
      ) : meetings.isSuccess && count === 0 ? (
        <p className="text-[12.5px]/[1.5] text-u-text3">No meetings with {firstName} on your team's calendars yet.</p>
      ) : null}

      {upcoming.length > 0 && (
        <>
          <div className="mb-1 font-mono text-[10px] font-semibold uppercase tracking-[.12em] text-u-text3">
            Upcoming
          </div>
          {upcoming.map((meeting) => {
            const opensPanel = offersNotetaker(meeting);
            return (
              <UpcomingMeeting
                key={meeting.id}
                meeting={meeting}
                menu={menuOf(
                  meeting,
                  opensPanel
                    ? { label: "Notetaker & questions…", icon: ICONS.mic, onSelect: () => setPanelOf(meeting.id) }
                    : { label: "Prepare questions…", icon: ICONS.checklist, onSelect: () => setPlanOf(meeting.id) },
                  meeting.editable && canReschedule ? () => setRescheduling(meeting) : undefined,
                  meeting.editable && hasActiveMailbox ? () => setCancelling(meeting) : undefined,
                )}
              />
            );
          })}
        </>
      )}

      {past.length > 0 && (
        <>
          <div className="mb-1 mt-1.5 font-mono text-[10px] font-semibold uppercase tracking-[.12em] text-u-text3">
            Past
          </div>
          {past.map((meeting) => (
            <div key={meeting.id} className="flex items-center gap-2.5 py-[5px] text-[12.5px] text-u-text2">
              <span className="w-[52px] flex-none font-mono text-[11.5px] text-u-text3">
                {shortDateOf(meeting.startsAt)}
              </span>
              <span className="min-w-0 flex-1">
                {meeting.title ?? "Untitled"}
                {meeting.ownerName && <span className="font-mono text-[11.5px] text-u-text3"> · {meeting.ownerName}</span>}
              </span>
              {notetaker.isSuccess &&
                menuOf(
                  meeting,
                  offersNotetaker(meeting) && !hasCallEnded(recordingOf(meeting))
                    ? { label: "Notetaker…", icon: ICONS.mic, onSelect: () => setPanelOf(meeting.id) }
                    : undefined,
                )}
            </div>
          ))}
        </>
      )}

      {canBook && <ZoomConnectControl className="mt-2" />}

      <p className="mt-2 font-mono text-[11px]/[1.5] text-u-text3">
        From your team's connected calendars. Only meetings with {firstName}'s addresses are kept, never the rest of
        anyone's calendar.
      </p>

      {isBooking && mailbox.data?.connection && (
        <BookCallDialog
          projectId={projectId}
          candidateId={candidateId}
          fullName={fullName}
          candidateStatus={candidateStatus}
          emails={emails}
          onClose={() => setIsBooking(false)}
        />
      )}

      {transcriptOf && (
        <TranscriptSheet
          projectId={projectId}
          candidateId={candidateId}
          recordingId={transcriptOf}
          onClose={() => setTranscriptOf(null)}
        />
      )}

      {panelMeeting && (
        <CallAssistantPanel
          projectId={projectId}
          candidateId={candidateId}
          meeting={panelMeeting}
          recording={recordingOf(panelMeeting)}
          mayRecord={mayRecord(panelMeeting)}
          mayStartNow={isRecordable(panelMeeting) && startable.has(panelMeeting.id)}
          offersQuestions={upcoming.includes(panelMeeting)}
          firstName={firstName}
          onPrepareQuestions={() => {
            setPanelOf(null);
            setPlanOf(panelMeeting.id);
          }}
          onOpenCopilot={handleOpenCopilot}
          onClose={() => setPanelOf(null)}
        />
      )}

      {planOf && (
        <InterviewPlanDialog
          projectId={projectId}
          candidateId={candidateId}
          meetingId={planOf}
          firstName={firstName}
          onClose={() => setPlanOf(null)}
        />
      )}

      {suggestionsOf && (
        <CallChangesDialog
          projectId={projectId}
          candidateId={candidateId}
          personId={personId}
          recordingId={suggestionsOf}
          firstName={firstName}
          onClose={() => setSuggestionsOf(null)}
        />
      )}

      {rescheduling && (
        <BookCallDialog
          projectId={projectId}
          candidateId={candidateId}
          fullName={fullName}
          candidateStatus={candidateStatus}
          emails={emails}
          meeting={rescheduling}
          onClose={() => setRescheduling(null)}
        />
      )}

      <ConfirmDialog
        open={cancelling !== null}
        title={`Cancel the call with ${fullName}?`}
        confirmLabel="Cancel call"
        cancelLabel="Keep call"
        pending={cancel.isPending}
        onConfirm={() => cancelling && cancel.mutate(cancelling)}
        onClose={() => setCancelling(null)}
      >
        {cancelling && (
          <p>
            {meetingWhenOf(cancelling.startsAt, cancelling.endsAt)} comes off your calendar, and {firstName} is told the
            call is cancelled. Their status stays where it is.
          </p>
        )}
      </ConfirmDialog>
    </section>
  );
}

function UpcomingMeeting({ meeting, menu }: { meeting: Meeting; menu: ReactNode }) {
  const tile = dateTileOf(meeting.startsAt);
  const when = meetingWhenOf(meeting.startsAt, meeting.endsAt);
  return (
    <div className="py-2">
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="w-[42px] flex-none rounded-[7px] border border-u-border py-[3px] text-center"
        >
          <span className="block font-mono text-[9.5px] font-semibold uppercase text-u-accent">{tile.month}</span>
          <span className="block text-[16px]/[1.2] font-semibold tabular-nums">{tile.day}</span>
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[13px] font-medium">{meeting.title ?? "Untitled"}</span>
          <span className="block font-mono text-[11.5px] text-u-text3">
            {when}
            {meeting.ownerName ? ` · ${meeting.ownerName}` : ""}
          </span>
          {meeting.viaLink && (
            <span className="mt-1 inline-flex rounded-[5px] bg-u-direct-tint px-[7px] py-px font-mono text-[10px] font-semibold text-u-direct">
              Booked through your link
            </span>
          )}
        </span>
        <span className="flex flex-none items-center gap-1.5">
          {meeting.joinUrl && (
            <a
              href={meeting.joinUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-[5px] rounded-[6px] border border-u-border px-[9px] py-1 text-[11.5px] font-medium text-u-text2 hover:text-u-text"
            >
              <Icon d={ICONS.video} size={12} />
              {meeting.videoProvider ?? "Join"}
            </a>
          )}
          {menu}
        </span>
      </div>
    </div>
  );
}
