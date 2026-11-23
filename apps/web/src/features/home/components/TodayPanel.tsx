import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { buttonClassName, LinesSkeleton } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatInstantTime, formatWeekdayDate } from "../../../lib/datetime/format";
import { recordPath } from "../lib/taskPath";
import { lengthOf, type TodayEvent } from "../lib/today";
import { CardEmpty, HomeCard } from "./HomeCard";
import type { MailboxGap } from "./NeedsAttention";

/**
 * Today: the meetings on the caller's calendar and the calls they set for today, on one timeline. The next one with
 * an executive gets a nudge to read up on them first.
 */
export function TodayPanel({
  events,
  pending,
  calendarRefused,
  mailboxGap,
  now = new Date(),
}: {
  events: TodayEvent[];
  pending: boolean;
  calendarRefused: boolean;
  mailboxGap: MailboxGap;
  now?: Date;
}) {
  const next = events.find((event) => new Date(event.startsAt) >= now);
  const nextWithPerson = events.find((event) => new Date(event.startsAt) >= now && event.personName);

  return (
    <HomeCard
      title="Today"
      subtitle={<span className="type-micro-label">{formatWeekdayDate(now)}</span>}
      aside={
        !pending && (
          <span className="text-note text-u-text3">{events.length === 1 ? "1 event" : `${events.length} events`}</span>
        )
      }
    >
      {pending ? (
        <div className="px-5 pb-4">
          <LinesSkeleton lines={3} />
        </div>
      ) : events.length === 0 ? (
        mailboxGap === "missing" ? (
          <CardEmpty
            icon={ICONS.calendar}
            title="No calendar connected"
            body="Connect your mailbox and today's meetings with executives show here, with a link to read up before each one."
            action={
              <Link to="/settings/integrations" className={buttonClassName("secondary", "px-3 py-1.5 text-note")}>
                Connect mailbox
              </Link>
            }
          />
        ) : (
          <CardEmpty
            icon={ICONS.calendar}
            title="A clear day"
            body={
              calendarRefused
                ? "Couldn't read your calendar just now. Calls you set on an executive still show here."
                : "No meetings with executives or calls set for today."
            }
          />
        )
      ) : (
        <div className="flex h-full flex-col gap-4 px-5 pb-5">
          <ol aria-label="Today's calendar">
            {events.map((event, index) => (
              <TimelineItem
                key={event.key}
                event={event}
                past={new Date(event.startsAt) < now}
                isNext={event === next}
                last={index === events.length - 1}
              />
            ))}
          </ol>
          {calendarRefused && <p className="text-meta text-u-text3">Couldn't read your calendar. Calls you set still show.</p>}
          {nextWithPerson ? (
            <BeforeNextCall event={nextWithPerson} />
          ) : (
            <p className="mt-auto text-meta text-u-text3">That's everything for today.</p>
          )}
        </div>
      )}
    </HomeCard>
  );
}

function TimelineItem({ event, past, isNext, last }: { event: TodayEvent; past: boolean; isNext: boolean; last: boolean }) {
  const headline = event.personName && !event.title.includes(event.personName) ? `${event.title} · ${event.personName}` : event.title;
  const where = [event.positionTitle, event.clientName].filter(Boolean).join(" · ");
  const meta = [event.minutes !== null ? lengthOf(event.minutes) : event.taskLabel, event.videoProvider].filter(Boolean).join(" · ");

  return (
    <li className="grid grid-cols-[44px_minmax(0,1fr)] gap-3">
      <span className={cn("pt-px font-mono text-note", isNext ? "text-u-accent" : "text-u-text3")}>
        {formatInstantTime(event.startsAt)}
      </span>
      <div className={cn("relative border-l border-u-border pl-4", !last && "pb-4")}>
        <span
          aria-hidden="true"
          className={cn(
            "absolute -left-[4.5px] top-1.5 size-2 rounded-full",
            isNext ? "bg-u-accent" : past ? "bg-u-border-strong" : "bg-u-text3",
          )}
        />
        <div className={cn("text-body font-medium", past && "text-u-text2")}>{headline}</div>
        {where && <div className="truncate text-meta text-u-text3">{where}</div>}
        {meta && (
          <div className="mt-0.5 flex items-center gap-1.5 text-meta text-u-text3">
            <Icon d={event.videoProvider ? ICONS.video : ICONS.phone} size={12} className="flex-none" />
            {meta}
            {event.joinUrl && (
              <a
                href={event.joinUrl}
                target="_blank"
                rel="noreferrer"
                className="ml-1 font-medium text-u-accent hover:underline"
              >
                Join
              </a>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

function BeforeNextCall({ event }: { event: TodayEvent }) {
  const firstName = event.personName?.split(/\s+/)[0] ?? "them";
  return (
    <div className="mt-auto rounded-u-chip border border-u-border bg-u-raised px-4 py-3">
      <div className="flex items-center gap-2 text-body font-medium">
        <Icon d={ICONS.fileText} size={14} className="text-u-text2" />
        Before your next call
      </div>
      <p className="mt-1 text-note text-u-text3">Review {firstName}'s profile and notes.</p>
      <Link
        to={recordPath(event.projectId ?? "", event.candidateId, event.personId)}
        className="mt-2 inline-flex items-center gap-1 text-note font-medium text-u-accent hover:underline"
      >
        View executive
        <Icon d={ICONS.arrowRight} size={12} />
      </Link>
    </div>
  );
}
