import { useEffect, useRef, useState } from "react";
import type { CSSProperties, PointerEvent, ReactNode } from "react";
import { cn } from "../../../lib/cn";
import { isBelowTableWidth } from "../../../lib/viewport";
import type { TimeSpan } from "../api/meetingApi";
import { slotDayPartsOf, slotTimeOf } from "../lib/meetingTimes";
import { wallClockOf } from "../lib/sendSchedule";
import {
  choiceOfDrag,
  choiceOfResize,
  endOf,
  MINUTES_IN_DAY,
  pickEdgesOn,
  pickHitOf,
  stretchesOn,
  type CallChoice,
  type DayStretch,
  type PickEdge,
  type PickHit,
} from "../lib/weekGrid";
import { formatClock } from "../../../lib/datetime";

/** One hour is 48px: a quarter hour is a 12px row, five minutes 4px. */
const PIXELS_PER_MINUTE = 0.8;
const GRID_HEIGHT = MINUTES_IN_DAY * PIXELS_PER_MINUTE;
/** Where the grid opens when nothing is picked and the week states no working hours. */
const OPENS_AT_MINUTES = 8 * 60;
/** How much of the hour before the opening time stays in view, so the first hour's label is never under the header. */
const LEAD_MINUTES = 30;
/** The least height, in pixels, a pick draws its resize grip in beneath its time. */
const GRIP_ROOM = 28;
const BUSY_HATCH =
  "repeating-linear-gradient(135deg, transparent 0 5px, var(--color-u-border) 5px 6px)";

/** A fresh pick dragged from `anchor`, or a picked call's edge dragged from where it was grabbed. */
type Drag =
  | { kind: "pick"; date: string; anchor: number; current: number }
  | { kind: "resize"; edge: PickEdge; date: string; edgeAt: number; origin: number; current: number };

/**
 * Book a call's week (`Outreach.dc.html?dialog=bookCall`): the consultant's busy times and working hours, day by day,
 * and a call picked by dragging from any time to any time — or a click, which takes the chosen length. Once picked, its
 * top or bottom edge drags to change the start or the end, a press inside it changes nothing, and a press elsewhere
 * picks afresh. Working hours only tint the grid; a busy stretch is the calendar's, so the pick over it is drawn but the
 * dialog refuses it. The "Pick a time" fields in the dialog's foot are the keyboard's way in.
 */
export function WeekTimeGrid({
  days,
  timeZone,
  busy,
  workingHours,
  choice,
  clickMinutes,
  minMinutes,
  maxMinutes,
  now,
  onChoose,
  className,
}: {
  days: string[];
  timeZone: string;
  busy: TimeSpan[];
  workingHours: TimeSpan[];
  choice: CallChoice | null;
  /** The length a click picks: the one the dialog has chosen. */
  clickMinutes: number;
  minMinutes: number;
  maxMinutes: number;
  now: Date;
  onChoose: (choice: CallChoice) => void;
  className?: string;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [hover, setHover] = useState<{ date: string; kind: PickHit["kind"] } | null>(null);
  const [focusedDay, setFocusedDay] = useState<string | null>(null);
  // A phone shows one day at a time, picked from a strip: seven 104px columns were a sideways scroll, and a thumb
  // dragging out a time kept landing on the wrong day.
  const [oneDayAtATime] = useState(isBelowTableWidth);

  const firstDay = days[0];
  useEffect(() => {
    const [hour, minute] = choice ? wallClockOf(new Date(choice.start), timeZone).time.split(":").map(Number) : [0, 0];
    const workStarts = days.flatMap((date) => stretchesOn(workingHours, date, timeZone).map((stretch) => stretch.top));
    const opensAt = choice ? hour * 60 + minute : workStarts.length > 0 ? Math.min(...workStarts) : OPENS_AT_MINUTES;
    scroller.current?.scrollTo?.({ top: Math.max(0, opensAt - LEAD_MINUTES) * PIXELS_PER_MINUTE });
    // Only when a week is first drawn: following each pick would scroll the grid out from under the pointer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firstDay, timeZone]);

  const minutesAt = (event: PointerEvent<HTMLDivElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    return Math.min(MINUTES_IN_DAY, Math.max(0, (event.clientY - box.top) / PIXELS_PER_MINUTE));
  };

  const hitAt = (date: string, at: number) => (choice ? pickHitOf(choice, date, at, timeZone) : null);

  const choiceOf = (held: Drag): CallChoice | null => {
    if (held.kind === "pick") {
      return choiceOfDrag(held.date, held.anchor, held.current, timeZone, { minMinutes, maxMinutes, clickMinutes });
    }
    if (!choice) return null;
    // From where the edge was, by how far the pointer moved: a press that never moves leaves the call as it was.
    const edgeTo = held.edgeAt + held.current - held.origin;
    return choiceOfResize(choice, held.edge, held.date, edgeTo, timeZone, { minMinutes, maxMinutes });
  };

  const handlePointerDown = (date: string) => (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    const at = minutesAt(event);
    const hit = hitAt(date, at);
    if (hit?.kind === "inside") return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setDrag(
      hit
        ? { kind: "resize", edge: hit.kind, date, edgeAt: hit.edgeAt, origin: at, current: at }
        : { kind: "pick", date, anchor: at, current: at },
    );
  };

  const handlePointerMove = (date: string) => (event: PointerEvent<HTMLDivElement>) => {
    const at = minutesAt(event);
    if (drag) {
      setDrag((held) => (held ? { ...held, current: at } : held));
      return;
    }
    const kind = hitAt(date, at)?.kind ?? null;
    setHover((held) =>
      kind === null ? null : held?.date === date && held.kind === kind ? held : { date, kind },
    );
  };

  const handlePointerUp = () => {
    if (!drag) return;
    const next = choiceOf(drag);
    if (next) onChoose(next);
    setDrag(null);
  };

  const cursorOn = (date: string) => {
    const kind = drag?.kind === "resize" ? (drag.date === date ? drag.edge : null) : hover?.date === date ? hover.kind : null;
    if (drag?.kind === "pick" || kind === null) return "cursor-crosshair";
    return kind === "inside" ? "cursor-default" : "cursor-ns-resize";
  };

  const dragged = drag ? choiceOf(drag) : null;
  const shown = dragged ?? choice;
  const nowSpan: TimeSpan[] = [{ start: new Date(0).toISOString(), end: now.toISOString() }];
  const today = wallClockOf(now, timeZone);
  const nowMinutes = Number(today.time.slice(0, 2)) * 60 + Number(today.time.slice(3, 5));
  const chosenDay = choice ? wallClockOf(new Date(choice.start), timeZone).date : null;
  const shownDay =
    [focusedDay, chosenDay, today.date].find((day): day is string => !!day && days.includes(day)) ?? days[0];

  return (
    <>
    {oneDayAtATime && (
      <div role="group" aria-label="Day" className="mb-2 flex gap-1.5 overflow-x-auto">
        {days.map((date) => {
          const parts = slotDayPartsOf(date);
          return (
            <button
              key={date}
              type="button"
              aria-pressed={date === shownDay}
              aria-label={`${parts.weekday} ${parts.day} ${parts.month}`}
              onClick={() => setFocusedDay(date)}
              className={cn(
                "flex min-w-11 flex-1 flex-col items-center rounded-u-control border px-1.5 py-1.5",
                date === shownDay ? "border-u-accent bg-u-accent-tint text-u-text" : "border-u-border text-u-text2",
              )}
            >
              <span className="type-summary-label">{parts.weekday}</span>
              <span className="text-body font-semibold">{parts.day}</span>
            </button>
          );
        })}
      </div>
    )}
    <div
      ref={scroller}
      role="group"
      aria-label="Your week"
      className={cn("relative max-h-[540px] overflow-auto rounded-u-chip border border-u-border bg-u-bg", className)}
    >
      <div className={cn("flex", !oneDayAtATime && "min-w-max")}>
        <div className="sticky start-0 z-20 w-12 flex-none bg-u-bg">
          <div className="sticky top-0 z-10 h-12 border-b border-u-border bg-u-bg" />
          <div className="relative" style={{ height: GRID_HEIGHT }}>
            {Array.from({ length: 24 }, (_, hour) => (
              <div
                key={hour}
                className="absolute end-1.5 -translate-y-1/2 font-mono text-meta text-u-text3"
                style={{ top: hour * 60 * PIXELS_PER_MINUTE }}
              >
                {hour === 0 ? "" : formatClock(`${String(hour).padStart(2, "0")}:00`)}
              </div>
            ))}
          </div>
        </div>
        {days.map((date) => {
          const parts = slotDayPartsOf(date);
          const pick = shown ? stretchesOn([{ start: shown.start, end: endOf(shown) }], date, timeZone) : [];
          const isToday = date === today.date;
          return (
            <div
              key={date}
              hidden={oneDayAtATime && date !== shownDay}
              className={cn(
                "w-[104px] flex-none border-s border-u-border md:w-auto md:min-w-[80px] md:flex-1",
                oneDayAtATime && "w-auto min-w-0 flex-1",
              )}
            >
              <div className="sticky top-0 z-10 flex h-12 flex-col items-center justify-center gap-0.5 border-b border-u-border bg-u-bg">
                <span className={cn("type-summary-label", isToday ? "text-u-accent" : "text-u-text3")}>
                  {parts.weekday}
                </span>
                <span
                  aria-current={isToday ? "date" : undefined}
                  className={cn(
                    "grid size-6 place-items-center rounded-full text-body font-semibold",
                    isToday ? "bg-u-accent-solid text-white" : "text-u-text",
                  )}
                >
                  {parts.day}
                </span>
              </div>
              <div
                data-testid={`day-${date}`}
                // On a phone a vertical swipe scrolls the day; a tap still picks, and a swipe cancels the drag.
                className={cn("relative select-none", oneDayAtATime ? "touch-pan-y" : "touch-none", cursorOn(date))}
                style={{
                  height: GRID_HEIGHT,
                  backgroundImage: `repeating-linear-gradient(to bottom, transparent 0 ${60 * PIXELS_PER_MINUTE - 1}px, var(--color-u-border) ${60 * PIXELS_PER_MINUTE - 1}px ${60 * PIXELS_PER_MINUTE}px)`,
                }}
                onPointerDown={handlePointerDown(date)}
                onPointerMove={handlePointerMove(date)}
                onPointerUp={handlePointerUp}
                onPointerLeave={() => setHover(null)}
                onPointerCancel={() => setDrag(null)}
              >
                {stretchesOn(workingHours, date, timeZone).map((stretch) => (
                  <Block key={`work-${stretch.top}`} stretch={stretch} className="bg-u-accent-tint" />
                ))}
                {stretchesOn(nowSpan, date, timeZone).map((stretch) => (
                  <Block key="past" stretch={stretch} className="bg-u-surface" />
                ))}
                {stretchesOn(busy, date, timeZone).map((stretch) => (
                  <Block
                    key={`busy-${stretch.top}`}
                    stretch={stretch}
                    className="inset-x-1 rounded-u-control border border-u-border-strong bg-u-raised px-1.5 py-0.5 text-meta font-medium text-u-text2"
                    style={{ backgroundImage: BUSY_HATCH }}
                  >
                    Busy
                  </Block>
                ))}
                {isToday && (
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute inset-x-0 z-20 h-0.5 bg-u-offlimits"
                    style={{ top: nowMinutes * PIXELS_PER_MINUTE - 1 }}
                  >
                    <span className="absolute -start-1 -top-1 size-2.5 rounded-full bg-u-offlimits" />
                  </div>
                )}
                {pick.map((stretch) => (
                  <Block
                    key="pick"
                    stretch={stretch}
                    className={cn(
                      "inset-x-1 z-10 rounded-u-control px-1.5 py-0.5 text-meta font-semibold shadow-md",
                      "bg-u-accent-solid text-white",
                    )}
                  >
                    {shown && `${slotTimeOf(shown.start, timeZone)}–${slotTimeOf(endOf(shown), timeZone)}`}
                    {shown &&
                      pickEdgesOn(shown, date, timeZone).endsHere &&
                      (stretch.bottom - stretch.top) * PIXELS_PER_MINUTE >= GRIP_ROOM && (
                        <span className="absolute inset-x-0 bottom-1 mx-auto h-[3px] w-[18px] rounded-full bg-current opacity-60" />
                      )}
                  </Block>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
    </>
  );
}

function Block({
  stretch,
  className,
  style,
  children,
}: {
  stretch: DayStretch;
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
}) {
  return (
    <div
      aria-hidden="true"
      className={cn("pointer-events-none absolute inset-x-0 overflow-hidden", className)}
      style={{
        ...style,
        top: stretch.top * PIXELS_PER_MINUTE,
        height: Math.max(4, (stretch.bottom - stretch.top) * PIXELS_PER_MINUTE),
      }}
    >
      {children}
    </div>
  );
}
