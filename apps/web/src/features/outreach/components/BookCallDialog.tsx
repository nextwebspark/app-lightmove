import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Modal, Skeleton, useToast } from "../../../components/ui";
import { Input, Select } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import type { CandidateEmail, CandidateStatus } from "../../candidates/api/types";
import * as meetingApi from "../api/meetingApi";
import type { MeetingVideo } from "../api/meetingApi";
import { slotDayLabelOf, slotTimeOf } from "../lib/meetingTimes";

type Length = "15" | "30" | "45";

const LENGTHS: { value: Length; label: string }[] = [
  { value: "15", label: "15 min" },
  { value: "30", label: "30 min" },
  { value: "45", label: "45 min" },
];

const VIDEO_LINKS: { value: MeetingVideo; label: string }[] = [
  { value: "GOOGLE_MEET", label: "Google Meet" },
  { value: "MICROSOFT_TEAMS", label: "Microsoft Teams" },
  { value: "NONE", label: "No video link" },
];

const DEFAULT_TITLE = "Confidential: first conversation";

/**
 * Book a call (`Outreach.dc.html?dialog=bookCall`): the consultant's free times over the next working
 * days, read off their own calendar in their own zone, and an invite sent from it. Booking moves the
 * person forward to Engaged and ends their sequence on this position, both on the server.
 */
export function BookCallDialog({
  projectId,
  candidateId,
  fullName,
  candidateStatus,
  emails,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Only someone still Identified or Contacted moves to Engaged; the toast says so only then. */
  candidateStatus: CandidateStatus;
  emails: CandidateEmail[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const firstName = fullName.trim().split(/\s+/)[0];
  const [length, setLength] = useState<Length>("30");
  const [chosenVideo, setChosenVideo] = useState<MeetingVideo | null>(null);
  const [slot, setSlot] = useState<string | null>(null);
  const [inviteAddress, setInviteAddress] = useState(emails[0]?.address ?? "");
  const [title, setTitle] = useState(DEFAULT_TITLE);

  const minutes = Number(length);
  const slots = useQuery({
    queryKey: meetingApi.MEETING_SLOTS_KEY(projectId, candidateId, minutes),
    queryFn: ({ signal }) => meetingApi.getMeetingSlots(projectId, candidateId, minutes, signal),
  });
  const timeZone = slots.data?.timeZone;
  const video: MeetingVideo = chosenVideo ?? (slots.data?.provider === "microsoft" ? "MICROSOFT_TEAMS" : "GOOGLE_MEET");
  const isSlotOffered = slot !== null && (slots.data?.days.some((day) => day.starts.includes(slot)) ?? false);

  const book = useMutation({
    mutationFn: () =>
      meetingApi.bookMeeting(projectId, candidateId, {
        startsAt: slot as string,
        minutes,
        video,
        inviteAddress,
        title: title.trim(),
      }),
    onSuccess: () => {
      const day = slots.data?.days.find((candidate) => candidate.starts.includes(slot as string));
      const movesForward = candidateStatus === "identified" || candidateStatus === "contacted";
      toast(
        `Invite sent to ${firstName} for ${day ? slotDayLabelOf(day.date) : "the call"}, ${slotTimeOf(slot as string, timeZone ?? "UTC")}.` +
          (movesForward ? " They moved to Engaged." : ""),
      );
      void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      onClose();
    },
    onError: (error) => {
      if (codeOf(error) === "MEETING_SLOT_TAKEN") {
        setSlot(null);
        void slots.refetch();
      }
      toast(messageFor(error));
    },
  });

  const handleLengthChange = (next: Length) => {
    setLength(next);
    setSlot(null);
  };

  const chosenDay = slots.data?.days.find((day) => slot !== null && day.starts.includes(slot));
  const summary =
    slot && chosenDay && timeZone
      ? `${slotDayLabelOf(chosenDay.date)} · ${slotTimeOf(slot, timeZone)} · ${minutes} min · invite from ${slots.data?.address}`
      : "Pick a time";

  const footer = (
    <div className="flex w-full items-center gap-2.5">
      <span className="min-w-0 truncate font-mono text-[12px] text-u-text3">{summary}</span>
      <Button
        type="button"
        className="ms-auto"
        disabled={!isSlotOffered || inviteAddress === "" || title.trim() === ""}
        loading={book.isPending}
        onClick={() => book.mutate()}
      >
        Send invite
      </Button>
    </div>
  );

  return (
    <Modal open onClose={onClose} title={`Book a call with ${fullName}`} footer={footer} className="md:w-[640px]">
      <p className="-mt-2 mb-3 font-mono text-[12px] text-u-text3">
        {slots.data ? `Free times on ${slots.data.address} · ${slots.data.timeZone}` : " "}
      </p>
      <div className="mb-3.5 flex flex-wrap items-center gap-3.5">
        <SegmentedControl label="Length" options={LENGTHS} value={length} onChange={handleLengthChange} />
        <label className="flex items-center gap-2 text-[12.5px] text-u-text2">
          Video
          <Select
            aria-label="Video link"
            value={video}
            onChange={(event) => setChosenVideo(event.target.value as MeetingVideo)}
            className="w-auto px-2 py-1 text-[12.5px]"
          >
            {VIDEO_LINKS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </label>
      </div>

      {slots.isPending ? (
        <Skeleton className="mb-4 h-40 w-full" />
      ) : slots.isError ? (
        <p role="alert" className="mb-4 text-[12.5px] text-u-offlimits">
          {messageFor(slots.error)}
        </p>
      ) : (
        <div className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {slots.data.days.map((day) => (
            <div key={day.date}>
              <div className="mb-1.5 text-center font-mono text-[11px] font-semibold text-u-text2">
                {slotDayLabelOf(day.date)}
              </div>
              <div className="flex flex-col gap-[5px]">
                {day.starts.map((start) => (
                  <button
                    key={start}
                    type="button"
                    aria-pressed={slot === start}
                    onClick={() => setSlot(start)}
                    className={cn(
                      "rounded-[6px] border px-2 py-[5px] font-mono text-[12px] transition",
                      slot === start
                        ? "border-u-accent-solid bg-u-accent-solid text-white"
                        : "border-u-border text-u-text2 hover:border-u-text3 hover:text-u-text",
                    )}
                  >
                    {slotTimeOf(start, slots.data.timeZone)}
                  </button>
                ))}
                {day.starts.length === 0 && (
                  <span className="text-center font-mono text-[11px] text-u-text3">Fully booked</span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-[72px_1fr] items-center gap-x-2.5 gap-y-2 font-mono text-[12.5px] text-u-text2">
        <span className="text-u-text3">Invite</span>
        <Select
          aria-label="Invite address"
          value={inviteAddress}
          onChange={(event) => setInviteAddress(event.target.value)}
          className="px-2 py-1.5 font-mono text-[12.5px]"
        >
          {emails.map((email) => (
            <option key={email.address} value={email.address}>
              {email.kind ? `${email.address} · ${email.kind}` : email.address}
            </option>
          ))}
        </Select>
        <span className="text-u-text3">Title</span>
        <Input
          aria-label="Meeting title"
          value={title}
          maxLength={200}
          onChange={(event) => setTitle(event.target.value)}
          className="px-2 py-1.5 text-[13px]"
        />
      </div>
      <p className="mt-2.5 pb-1 font-mono text-[11.5px]/[1.5] text-u-text3">
        The title is what {firstName} sees. Keep the business unit out of it unless you mean to name it.
      </p>
    </Modal>
  );
}
