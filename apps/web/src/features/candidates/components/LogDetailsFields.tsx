import { DateInput } from "../../../components/ui/DateInput";
import { Input, Select } from "../../../components/ui";
import type { Meeting } from "../../outreach/api/meetingApi";
import { meetingWhenOf } from "../../outreach/lib/meetingTimes";
import type { PersonNoteKind } from "../api/types";

/** When a logged call, message or meeting happened, its title, and for a meeting which one it was. */
export interface LogDetails {
  /** yyyy-MM-dd in the viewer's own zone. */
  date: string;
  /** HH:mm in the viewer's own zone. */
  time: string;
  title: string;
  /** A calendar meeting's id; null for one that happened outside Uncava, or for a call or a message. */
  meetingId: string | null;
}

export const OUTSIDE_UNCAVA = "";

/** Now, in the viewer's own zone: what a log is stamped with unless someone says otherwise. */
export function logDetailsNow(meetingId: string | null = null): LogDetails {
  const now = new Date();
  return { date: localDateOf(now), time: localTimeOf(now), title: "", meetingId };
}

/** The request fields a log's details become; a chosen meeting brings its own time and, untyped, its title. */
export function logPayloadOf(details: LogDetails) {
  const title = details.title.trim() || null;
  if (details.meetingId) return { meetingId: details.meetingId, title, occurredAt: null };
  const at = new Date(`${details.date}T${details.time || "00:00"}`);
  return { occurredAt: Number.isNaN(at.getTime()) ? null : at.toISOString(), title, meetingId: null };
}

/**
 * The log dialog's first row: for a meeting, which calendar meeting it was (or one outside Uncava); then when it
 * happened — now unless changed — and an optional title. A chosen meeting brings its own date and time.
 */
export function LogDetailsFields({
  kind,
  pastMeetings,
  value,
  onChange,
}: {
  kind: PersonNoteKind;
  pastMeetings: readonly Meeting[];
  value: LogDetails;
  onChange: (next: LogDetails) => void;
}) {
  const chosen = kind === "meeting" ? (pastMeetings.find((meeting) => meeting.id === value.meetingId) ?? null) : null;
  const shownDate = chosen ? localDateOf(new Date(chosen.startsAt)) : value.date;
  const shownTime = chosen ? localTimeOf(new Date(chosen.startsAt)) : value.time;
  const today = localDateOf(new Date());
  return (
    <div className="mb-2 flex flex-col gap-2">
      {kind === "meeting" && (
        <label className="flex flex-col gap-1">
          <span className="text-meta font-medium text-u-text3">Which meeting</span>
          <Select
            density="compact"
            value={value.meetingId ?? OUTSIDE_UNCAVA}
            onChange={(event) => onChange({ ...value, meetingId: event.target.value || null })}
          >
            {pastMeetings.map((meeting) => (
              <option key={meeting.id} value={meeting.id}>
                {meeting.title ?? "Untitled"} · {meetingWhenOf(meeting.startsAt, meeting.endsAt)}
              </option>
            ))}
            <option value={OUTSIDE_UNCAVA}>Outside Uncava — set the date and title</option>
          </Select>
        </label>
      )}
      <div className="flex flex-wrap items-start gap-2">
        <div className="flex flex-col gap-1">
          <span className="text-meta font-medium text-u-text3">When</span>
          <div className="flex gap-2" aria-disabled={chosen !== null || undefined}>
            {chosen ? (
              <span className="rounded-u-control border border-u-border px-3 py-2 text-note text-u-text2">
                {meetingWhenOf(chosen.startsAt, chosen.endsAt)}
              </span>
            ) : (
              <>
                <DateInput
                  value={shownDate}
                  max={today}
                  ariaLabel="Date it happened"
                  onChange={(date) => onChange({ ...value, date })}
                  className="w-[150px]"
                />
                <Input
                  type="time"
                  aria-label="Time it happened"
                  value={shownTime}
                  onChange={(event) => onChange({ ...value, time: event.target.value })}
                  className="w-[130px]"
                />
              </>
            )}
          </div>
        </div>
        <label className="flex min-w-[160px] flex-1 flex-col gap-1">
          <span className="text-meta font-medium text-u-text3">Title (optional)</span>
          <Input
            value={value.title}
            maxLength={200}
            placeholder={chosen?.title ?? (kind === "call" ? "e.g. First call" : kind === "linkedin" ? "e.g. InMail reply" : "e.g. Coffee at DIFC")}
            onChange={(event) => onChange({ ...value, title: event.target.value.replace(/[\r\n]/g, " ") })}
          />
        </label>
      </div>
    </div>
  );
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function localDateOf(at: Date): string {
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`;
}

function localTimeOf(at: Date): string {
  return `${pad(at.getHours())}:${pad(at.getMinutes())}`;
}
