import { useEffect, useRef, useState } from "react";
import type { CSSProperties, PointerEvent, ReactNode } from "react";
import { cn } from "../../../lib/cn";
import type { TimeSpan } from "../api/meetingApi";
import { slotDayPartsOf, slotTimeOf } from "../lib/meetingTimes";
import { readsAsCards } from "../../../lib/viewport";
import { wallClockOf } from "../lib/sendSchedule";
import {
  choiceOfDrag,
  endOf,
  MINUTES_IN_DAY,
  stretchesOn,
  type CallChoice,
  type DayStretch,
} from "../lib/weekGrid";

/** One hour is 48px: a quarter hour is a 12px row, five minutes 4px. */
const PIXELS_PER_MINUTE = 0.8;
const GRID_HEIGHT = MINUTES_IN_DAY * PIXELS_PER_MINUTE;
/** Where the grid opens when nothing is picked and the week states no working hours. */
const OPENS_AT_MINUTES = 8 * 60;
/** How much of the hour before the opening time stays in view, so the first hour's label is never under the header. */
const LEAD_MINUTES = 30;
const BUSY_HATCH =
  "repeating-linear-gradient(135deg, transparent 0 5px, var(--color-u-border) 5px 6px)";

interface Drag {
  date: string;
  anchor: number;
  current: number;
}

/**
 * Book a call's week (`Outreach.dc.html?dialog=bookCall`): the consultant's busy times and working hours, day by day,
 * and a call picked by dragging from any time to any time — or a click, which takes the chosen length. Working hours
 * only tint the grid; a busy stretch is the calendar's, so the pick over it is drawn but the dialog refuses it. The
 * "Pick a time" fields beside it are the keyboard's way in.
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
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [focusedDay, setFocusedDay] = useState<string | null>(null);
  const [oneDayAtATime] = useState(readsAsCards);

  const firstDay = days[0];
  useEffect(() => {
    const [hour, minute] = choice
      ? wallClockOf(new Date(choice.start), timeZone)
          .time.split(":")
          .map(Number)
      : [0, 0];
    const workStarts = days.flatMap((date) =>
      stretchesOn(workingHours, date, timeZone).map((stretch) => stretch.top),
    );
    const opensAt = choice
      ? hour * 60 + minute
      : workStarts.length > 0
        ? Math.min(...workStarts)
        : OPENS_AT_MINUTES;
    scroller.current?.scrollTo?.({
      top: Math.max(0, opensAt - LEAD_MINUTES) * PIXELS_PER_MINUTE,
    });
    // Only when a week is first drawn: following each pick would scroll the grid out from under the pointer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firstDay, timeZone]);

  const minutesAt = (event: PointerEvent<HTMLDivElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    return Math.min(
      MINUTES_IN_DAY,
      Math.max(0, (event.clientY - box.top) / PIXELS_PER_MINUTE),
    );
  };

  const handlePointerDown =
    (date: string) => (event: PointerEvent<HTMLDivElement>) => {
      if (event.button !== 0) return;
      event.currentTarget.setPointerCapture?.(event.pointerId);
      const at = minutesAt(event);
      setDrag({ date, anchor: at, current: at });
    };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!drag) return;
    const at = minutesAt(event);
    setDrag((held) => (held ? { ...held, current: at } : held));
  };

  const handlePointerUp = () => {
    if (!drag) return;
    onChoose(
      choiceOfDrag(drag.date, drag.anchor, drag.current, timeZone, {
        minMinutes,
        maxMinutes,
        clickMinutes,
      }),
    );
    setDrag(null);
  };

  const dragged = drag
    ? choiceOfDrag(drag.date, drag.anchor, drag.current, timeZone, {
        minMinutes,
        maxMinutes,
        clickMinutes,
      })
    : null;
  const shown = dragged ?? choice;
  const nowSpan: TimeSpan[] = [
    { start: new Date(0).toISOString(), end: now.toISOString() },
  ];
  const today = wallClockOf(now, timeZone);
  const nowMinutes =
    Number(today.time.slice(0, 2)) * 60 + Number(today.time.slice(3, 5));
  const chosenDay = choice
    ? wallClockOf(new Date(choice.start), timeZone).date
    : null;
  // Below `md` the week is one day at a time, picked from the strip: seven 104px columns were a sideways scroll
  // on a phone, and a thumb dragging to pick a time kept landing on the wrong day.
  const phoneDay =
    [focusedDay, chosenDay, today.date].find(
      (day): day is string => !!day && days.includes(day),
    ) ?? days[0];

  return (
    <>
      {oneDayAtATime && (
        <div
          role="group"
          aria-label="Day"
          className="mb-2 flex gap-1.5 overflow-x-auto"
        >
          {days.map((date) => {
            const parts = slotDayPartsOf(date);
            const isShown = date === phoneDay;
            return (
              <button
                key={date}
                type="button"
                aria-pressed={isShown}
                onClick={() => setFocusedDay(date)}
                className={cn(
                  "flex min-w-11 flex-1 flex-col items-center rounded-u-control border px-1.5 py-1.5",
                  isShown
                    ? "border-u-accent bg-u-accent-tint text-u-text"
                    : "border-u-border text-u-text2",
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
        className="relative max-h-[540px] overflow-auto rounded-u-chip border border-u-border bg-u-bg"
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
                  {hour === 0 ? "" : `${String(hour).padStart(2, "0")}:00`}
                </div>
              ))}
            </div>
          </div>
          {days.map((date) => {
            const parts = slotDayPartsOf(date);
            const pick = shown
              ? stretchesOn(
                  [{ start: shown.start, end: endOf(shown) }],
                  date,
                  timeZone,
                )
              : [];
            const isToday = date === today.date;
            return (
              <div
                key={date}
                className={cn(
                  "w-[104px] flex-none border-s border-u-border md:w-auto md:min-w-[80px] md:flex-1",
                  oneDayAtATime && "w-auto min-w-0 flex-1",
                  oneDayAtATime && date !== phoneDay && "hidden",
                )}
              >
                <div className="sticky top-0 z-10 flex h-12 flex-col items-center justify-center gap-0.5 border-b border-u-border bg-u-bg">
                  <span
                    className={cn(
                      "type-summary-label",
                      isToday ? "text-u-accent" : "text-u-text3",
                    )}
                  >
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
                  // A phone keeps its vertical swipe for scrolling the day: a tap still picks, a swipe cancels the drag.
                  className={cn(
                    "relative cursor-crosshair select-none",
                    oneDayAtATime ? "touch-pan-y" : "touch-none",
                  )}
                  style={{
                    height: GRID_HEIGHT,
                    backgroundImage: `repeating-linear-gradient(to bottom, transparent 0 ${60 * PIXELS_PER_MINUTE - 1}px, var(--color-u-border) ${60 * PIXELS_PER_MINUTE - 1}px ${60 * PIXELS_PER_MINUTE}px)`,
                  }}
                  onPointerDown={handlePointerDown(date)}
                  onPointerMove={handlePointerMove}
                  onPointerUp={handlePointerUp}
                  onPointerCancel={() => setDrag(null)}
                >
                  {stretchesOn(workingHours, date, timeZone).map((stretch) => (
                    <Block
                      key={`work-${stretch.top}`}
                      stretch={stretch}
                      className="bg-u-accent-tint"
                    />
                  ))}
                  {stretchesOn(nowSpan, date, timeZone).map((stretch) => (
                    <Block
                      key="past"
                      stretch={stretch}
                      className="bg-u-surface"
                    />
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
                      {shown &&
                        `${slotTimeOf(shown.start, timeZone)}–${slotTimeOf(endOf(shown), timeZone)}`}
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
      className={cn(
        "pointer-events-none absolute inset-x-0 overflow-hidden",
        className,
      )}
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
