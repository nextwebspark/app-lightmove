import { Icon, ICONS } from "../../../components/layout/Icon";
import { DateInput } from "../../../components/ui/DateInput";
import { Popover } from "../../../components/ui/Popover";
import { TimeInput } from "../../../components/ui/TimeInput";
import { cn } from "../../../lib/cn";
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

/** A log dated later than now, which the server refuses: a meeting chosen from the calendar brings its own past time. */
export function isLogInFuture(details: LogDetails, kind: PersonNoteKind): boolean {
  if (kind === "meeting" && details.meetingId) return false;
  const at = new Date(`${details.date}T${details.time || "00:00"}`);
  return !Number.isNaN(at.getTime()) && at.getTime() > Date.now();
}

/**
 * The log dialog's details as one row of chips above an optional title: for a meeting, which calendar meeting it
 * was (or one outside Uncava); then when it happened, now unless changed. A chosen meeting brings its own time.
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
  const inFuture = isLogInFuture(value, kind);
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex flex-wrap items-center gap-1.5">
        {kind === "meeting" && (
          <MeetingChip
            meetings={pastMeetings}
            chosen={chosen}
            onChoose={(meetingId) => onChange({ ...value, meetingId })}
          />
        )}
        {!chosen && <WhenChip value={value} invalid={inFuture} onChange={onChange} />}
      </div>
      {inFuture && (
        <p id={WHEN_ERROR_ID} role="alert" className="text-meta text-u-offlimits">
          This time hasn&rsquo;t happened yet.
        </p>
      )}
      <input
        aria-label="Title (optional)"
        value={value.title}
        maxLength={200}
        placeholder={chosen?.title ?? `Add a title, e.g. ${TITLE_EXAMPLES[kind] ?? TITLE_EXAMPLES.meeting}`}
        onChange={(event) => onChange({ ...value, title: event.target.value.replace(/[\r\n]/g, " ") })}
        className="w-full bg-transparent text-body font-semibold text-u-text outline-none placeholder:font-normal placeholder:text-u-text3"
      />
    </div>
  );
}

const WHEN_ERROR_ID = "log-when-error";

const TITLE_EXAMPLES: Partial<Record<PersonNoteKind, string>> = {
  call: "First call",
  linkedin: "InMail reply",
  meeting: "Coffee at DIFC",
};

/** What a log was most likely just after: the moments a consultant reaches for without typing a time. */
const QUICK_TIMES: readonly { label: string; minutesAgo: number }[] = [
  { label: "Just now", minutesAgo: 0 },
  { label: "15 minutes ago", minutesAgo: 15 },
  { label: "1 hour ago", minutesAgo: 60 },
  { label: "Yesterday, same time", minutesAgo: 24 * 60 },
];

function WhenChip({
  value,
  invalid,
  onChange,
}: {
  value: LogDetails;
  invalid: boolean;
  onChange: (next: LogDetails) => void;
}) {
  const label = whenLabelOf(value.date, value.time);
  return (
    <Popover
      label={`When it happened: ${label}`}
      hasPopup="dialog"
      width={268}
      triggerClassName={chipClassOf(invalid)}
      trigger={(open) => (
        <>
          <Icon d={ICONS.clock} size={13} className={invalid ? "text-u-offlimits" : "text-u-text3"} />
          <span>{label}</span>
          <Icon d={ICONS.chevronDown} size={12} className={cn("text-u-text3 transition", open && "rotate-180")} />
        </>
      )}
    >
      {(close) => (
        <div className="flex flex-col">
          {QUICK_TIMES.map((quick) => (
            <button
              key={quick.label}
              type="button"
              onClick={() => {
                const at = new Date(Date.now() - quick.minutesAgo * 60_000);
                onChange({ ...value, date: localDateOf(at), time: localTimeOf(at) });
                close();
              }}
              className={MENU_ITEM}
            >
              {quick.label}
            </button>
          ))}
          <div className="my-1.5 border-t border-u-border" />
          <div className="flex gap-2 px-1 pb-1">
            <DateInput
              value={value.date}
              max={localDateOf(new Date())}
              ariaLabel="Date it happened"
              onChange={(date) => onChange({ ...value, date })}
              className="flex-1 py-1.5"
            />
            <TimeInput
              value={value.time}
              ariaLabel="Time it happened"
              invalid={invalid}
              describedBy={invalid ? WHEN_ERROR_ID : undefined}
              onChange={(time) => onChange({ ...value, time })}
              className="w-[96px] flex-none py-1.5"
            />
          </div>
        </div>
      )}
    </Popover>
  );
}

function MeetingChip({
  meetings,
  chosen,
  onChoose,
}: {
  meetings: readonly Meeting[];
  chosen: Meeting | null;
  onChoose: (meetingId: string | null) => void;
}) {
  const label = chosen ? (chosen.title ?? "Untitled") : "Outside Uncava";
  return (
    <Popover
      label={`Which meeting: ${label}`}
      hasPopup="listbox"
      width={340}
      triggerClassName={chipClassOf(false)}
      trigger={(open) => (
        <>
          <Icon d={ICONS.video} size={13} className="text-u-text3" />
          <span className="max-w-[220px] truncate">{label}</span>
          {chosen && (
            <>
              <span aria-hidden className="h-3 w-px flex-none bg-u-border-strong" />
              <span className="flex-none text-u-text3">{shortDayOf(chosen.startsAt)}</span>
            </>
          )}
          <Icon d={ICONS.chevronDown} size={12} className={cn("text-u-text3 transition", open && "rotate-180")} />
        </>
      )}
    >
      {(close) => {
        const choose = (meetingId: string | null) => {
          onChoose(meetingId);
          close();
        };
        return (
          <div role="listbox" aria-label="Which meeting" className="flex flex-col">
            {meetings.map((meeting) => (
              <MeetingOption
                key={meeting.id}
                selected={meeting.id === chosen?.id}
                title={meeting.title ?? "Untitled"}
                detail={`${meetingWhenOf(meeting.startsAt, meeting.endsAt)}${meeting.ownerName ? ` · ${meeting.ownerName}` : ""}`}
                onSelect={() => choose(meeting.id)}
              />
            ))}
            {meetings.length > 0 && <div className="my-1.5 border-t border-u-border" />}
            <MeetingOption
              selected={chosen === null}
              title="Outside Uncava"
              detail="Set the date and time yourself"
              onSelect={() => choose(null)}
            />
          </div>
        );
      }}
    </Popover>
  );
}

function MeetingOption({
  selected,
  title,
  detail,
  onSelect,
}: {
  selected: boolean;
  title: string;
  detail: string;
  onSelect: () => void;
}) {
  return (
    <button type="button" role="option" aria-selected={selected} onClick={onSelect} className={cn(MENU_ITEM, "flex gap-2")}>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-medium text-u-text">{title}</span>
        <span className="block truncate font-mono text-meta text-u-text3">{detail}</span>
      </span>
      {selected && <Icon d={ICONS.check} size={14} className="mt-0.5 flex-none text-u-accent" />}
    </button>
  );
}

const MENU_ITEM =
  "w-full rounded-u-control px-2.5 py-1.5 text-start text-note text-u-text2 hover:bg-u-raised focus-visible:outline-2 focus-visible:outline-u-accent";

function chipClassOf(invalid: boolean): string {
  return cn(
    "inline-flex h-7 max-w-full items-center gap-1.5 rounded-full border bg-u-surface px-2.5 text-note transition",
    "focus-visible:outline-2 focus-visible:outline-u-accent",
    invalid
      ? "border-u-offlimits text-u-offlimits"
      : "border-u-border text-u-text2 hover:border-u-border-strong hover:text-u-text",
  );
}

/** "Today, 15:14", "Yesterday, 09:30", "3 Nov, 15:14", or with the year once it is not this one. */
export function whenLabelOf(date: string, time: string, now: Date = new Date()): string {
  const day = new Date(`${date}T00:00:00`);
  if (Number.isNaN(day.getTime())) return time || "Pick a time";
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const dayWords =
    date === localDateOf(now)
      ? "Today"
      : date === localDateOf(yesterday)
        ? "Yesterday"
        : day.toLocaleDateString("en-GB", {
            day: "numeric",
            month: "short",
            ...(day.getFullYear() === now.getFullYear() ? {} : { year: "numeric" }),
          });
  return time ? `${dayWords}, ${time}` : dayWords;
}

function shortDayOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
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
