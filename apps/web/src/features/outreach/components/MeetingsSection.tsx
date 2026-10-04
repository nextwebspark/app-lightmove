import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateEmail, CandidateStatus } from "../../candidates/api/types";
import * as meetingApi from "../api/meetingApi";
import type { Meeting } from "../api/meetingApi";
import { dateTileOf, meetingWhenOf, shortDateOf } from "../lib/meetingTimes";
import { useMailbox } from "../lib/useMailbox";
import { BookCallDialog } from "./BookCallDialog";
import { ZoomConnectControl } from "./ZoomConnectControl";

/**
 * The executive drawer's Meetings section (`Outreach.dc.html?drawer=`): calls with them on the team's
 * connected calendars, and Book a call from the consultant's own. Always open, unlike the folds below
 * it. Staff-only; Book a call is never offered for someone marked do not contact.
 */
export function MeetingsSection({
  projectId,
  candidateId,
  personId,
  fullName,
  candidateStatus,
  emails,
}: {
  projectId: string;
  candidateId: string;
  personId: string;
  fullName: string;
  candidateStatus: CandidateStatus;
  emails: CandidateEmail[];
}) {
  const meetings = useQuery({
    queryKey: meetingApi.MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => meetingApi.getMeetings(projectId, candidateId, signal),
  });
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(personId),
    queryFn: ({ signal }) => poolApi.getPerson(personId, signal),
  });
  const mailbox = useMailbox();
  const [isBooking, setIsBooking] = useState(false);

  const firstName = fullName.trim().split(/\s+/)[0];
  const upcoming = meetings.data?.upcoming ?? [];
  const past = meetings.data?.past ?? [];
  const count = upcoming.length + past.length;
  const canBook =
    mailbox.data?.offered === true &&
    mailbox.data.connection?.status === "ACTIVE" &&
    record.isSuccess &&
    !record.data.doNotContact &&
    emails.length > 0;

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
          <div className="mb-1 font-mono text-[10px] font-semibold uppercase tracking-[.12em] text-u-text3">Upcoming</div>
          {upcoming.map((meeting) => (
            <UpcomingMeeting key={meeting.id} meeting={meeting} />
          ))}
        </>
      )}

      {past.length > 0 && (
        <>
          <div className="mb-1 mt-1.5 font-mono text-[10px] font-semibold uppercase tracking-[.12em] text-u-text3">
            Past
          </div>
          {past.map((meeting) => (
            <div key={meeting.id} className="flex items-baseline gap-2.5 py-[5px] text-[12.5px] text-u-text2">
              <span className="w-[52px] flex-none font-mono text-[11.5px] text-u-text3">
                {shortDateOf(meeting.startsAt)}
              </span>
              <span className="min-w-0 flex-1">
                {meeting.title ?? "Untitled"}
                {meeting.ownerName && (
                  <span className="font-mono text-[11.5px] text-u-text3"> · {meeting.ownerName}</span>
                )}
              </span>
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
    </section>
  );
}

function UpcomingMeeting({ meeting }: { meeting: Meeting }) {
  const tile = dateTileOf(meeting.startsAt);
  const when = meetingWhenOf(meeting.startsAt, meeting.endsAt);
  return (
    <div className="flex items-start gap-3 py-2">
      <span aria-hidden="true" className="w-[42px] flex-none rounded-[7px] border border-u-border py-[3px] text-center">
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
      {meeting.joinUrl && (
        <a
          href={meeting.joinUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex flex-none items-center gap-[5px] rounded-[6px] border border-u-border px-[9px] py-1 text-[11.5px] font-medium text-u-text2 hover:text-u-text"
        >
          <Icon d={ICONS.video} size={12} />
          {meeting.videoProvider ?? "Join"}
        </a>
      )}
    </div>
  );
}
