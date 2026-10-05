import { Icon, ICONS } from "../../../components/layout/Icon";
import { DateInput } from "../../../components/ui/DateInput";
import { cn } from "../../../lib/cn";
import type { SlotDay } from "../api/meetingApi";
import { shiftDateOf, slotDayLabelOf, slotDayPartsOf, slotTimeOf } from "../lib/meetingTimes";

/** Book a call's ← date → control: a page back, a jump to any day, a page ahead. */
export function SlotPager({
  firstShown,
  lastShown,
  earliestDate,
  latestDate,
  previousFrom,
  isFetching,
  onShowFrom,
}: {
  firstShown: string;
  lastShown: string;
  earliestDate: string;
  latestDate: string;
  /** Where the page before starts, as the server counts working days; null on the first page. */
  previousFrom: string | null;
  isFetching: boolean;
  onShowFrom: (day: string) => void;
}) {
  return (
    <div className="ms-auto flex items-center gap-1">
      <button
        type="button"
        aria-label="Earlier days"
        disabled={previousFrom === null || isFetching}
        onClick={() => previousFrom && onShowFrom(previousFrom)}
        className={PAGER_CLASS}
      >
        <Icon d={ICONS.arrowLeft} size={14} />
      </button>
      <DateInput
        value={firstShown}
        min={earliestDate}
        max={latestDate}
        onChange={(day) => day && onShowFrom(day)}
        ariaLabel="Show times from"
        className="w-[148px] px-2.5 py-1.5 text-[12.5px]"
      />
      <button
        type="button"
        aria-label="Later days"
        disabled={lastShown >= latestDate || isFetching}
        onClick={() => onShowFrom(shiftDateOf(lastShown, 1))}
        className={PAGER_CLASS}
      >
        <Icon d={ICONS.arrowRight} size={14} />
      </button>
    </div>
  );
}

/** One tile per day of the page, with how many times it has free. */
export function SlotDayTiles({
  days,
  activeDate,
  onChoose,
}: {
  days: SlotDay[];
  activeDate: string | undefined;
  onChoose: (date: string) => void;
}) {
  return (
    <div className="mb-5 grid grid-cols-5 gap-1.5 sm:gap-2">
      {days.map((day) => {
        const parts = slotDayPartsOf(day.date);
        const isActive = day.date === activeDate;
        const isFull = day.starts.length === 0;
        return (
          <button
            key={day.date}
            type="button"
            aria-pressed={isActive}
            aria-label={`${slotDayLabelOf(day.date)}, ${isFull ? "fully booked" : `${day.starts.length} free`}`}
            onClick={() => onChoose(day.date)}
            className={cn(
              "flex flex-col items-center rounded-[8px] border px-1 py-2 transition",
              isActive
                ? "border-u-accent bg-u-accent-tint text-u-text"
                : "border-u-border text-u-text2 hover:border-u-text3 hover:text-u-text",
              isFull && !isActive && "opacity-60",
            )}
          >
            <span className="type-summary-label text-u-text3">{parts.weekday}</span>
            <span className="mt-0.5 text-[20px] font-semibold leading-none">{parts.day}</span>
            <span className="mt-1 text-[11px] text-u-text3">{parts.month}</span>
            <span className={cn("mt-1.5 font-mono text-[10.5px]", isFull ? "text-u-text3" : "text-u-accent")}>
              {isFull ? "Full" : `${day.starts.length} free`}
            </span>
          </button>
        );
      })}
    </div>
  );
}

/** A labelled run of free starts — Morning or Afternoon — drawn nowhere when it has none. */
export function SlotGroup({
  label,
  starts,
  timeZone,
  chosen,
  onChoose,
}: {
  label: string;
  starts: string[];
  timeZone: string | undefined;
  chosen: string | null;
  onChoose: (start: string) => void;
}) {
  if (starts.length === 0 || !timeZone) return null;
  return (
    <div className="mb-4">
      <div className="mb-2 font-mono text-[11px] text-u-text3">{label}</div>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
        {starts.map((start) => (
          <button
            key={start}
            type="button"
            aria-pressed={chosen === start}
            onClick={() => onChoose(start)}
            className={cn(
              "rounded-[6px] border px-2 py-2 font-mono text-[12.5px] transition",
              chosen === start
                ? "border-u-accent-solid bg-u-accent-solid text-white"
                : "border-u-border text-u-text2 hover:border-u-accent hover:text-u-text",
            )}
          >
            {slotTimeOf(start, timeZone)}
          </button>
        ))}
      </div>
    </div>
  );
}

const PAGER_CLASS =
  "rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-text disabled:pointer-events-none disabled:opacity-40";
