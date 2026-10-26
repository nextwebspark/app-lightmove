import { cn } from "../../../lib/cn";
import type { Weekday } from "../../outreach/api/sequenceApi";
import { HALF_HOURS, MONDAY_TO_FRIDAY, SUNDAY_TO_THURSDAY, WEEKDAYS } from "../../outreach/lib/sendSchedule";
import { DEFAULT_DAY_HOURS, presetOf, weekProblemOf, type WeekDraft } from "../lib/weeklyHours";

const STARTS = HALF_HOURS;
const ENDS = [...HALF_HOURS.slice(1), "23:59"];

const PRESETS: readonly [string, Weekday[]][] = [
  ["Mon–Fri", MONDAY_TO_FRIDAY],
  ["Sun–Thu", SUNDAY_TO_THURSDAY],
];

/**
 * A working week, day by day (`Settings.dc.html`, Meetings & email and Availability): each day switched on or off
 * with its own hours, so a short Thursday is a Thursday that ends early.
 */
export function WeeklyHoursEditor({
  value,
  onChange,
  disabled = false,
}: {
  value: WeekDraft;
  onChange: (next: WeekDraft) => void;
  disabled?: boolean;
}) {
  const problem = weekProblemOf(value);
  const setDay = (day: Weekday, hours: WeekDraft[Weekday]) => onChange({ ...value, [day]: hours });

  return (
    <div role="group" aria-label="Working hours" aria-disabled={disabled || undefined}>
      <ul className="divide-y divide-u-border">
        {WEEKDAYS.map(({ day, label }) => {
          const hours = value[day];
          return (
            <li key={day} className="flex flex-wrap items-center gap-2.5 py-2">
              <button
                type="button"
                aria-pressed={hours !== null}
                aria-label={`Work on ${label}`}
                disabled={disabled}
                onClick={() => setDay(day, hours ? null : { ...DEFAULT_DAY_HOURS })}
                className={cn(
                  "w-14 rounded-full border py-1 text-note font-medium transition disabled:cursor-not-allowed",
                  hours
                    ? "border-u-accent bg-u-accent-tint text-u-text"
                    : "border-u-border bg-u-surface text-u-text3 hover:text-u-text",
                )}
              >
                {label}
              </button>
              {hours ? (
                <span className="flex items-center gap-2 font-mono text-note text-u-text2">
                  <TimeSelect
                    label={`${label} starts`}
                    value={hours.start}
                    choices={STARTS}
                    disabled={disabled}
                    onChange={(start) => setDay(day, { ...hours, start })}
                  />
                  to
                  <TimeSelect
                    label={`${label} ends`}
                    value={hours.end}
                    choices={ENDS}
                    disabled={disabled}
                    onChange={(end) => setDay(day, { ...hours, end })}
                  />
                </span>
              ) : (
                <span className="font-mono text-note text-u-text3">Not working</span>
              )}
            </li>
          );
        })}
      </ul>
      <div className="mt-2 flex items-center gap-3 text-note">
        {PRESETS.map(([label, days]) => (
          <button
            key={label}
            type="button"
            disabled={disabled}
            onClick={() => onChange(presetOf(value, days))}
            className="font-medium text-u-accent disabled:text-u-text3"
          >
            {label}
          </button>
        ))}
      </div>
      {problem && !disabled && (
        <p role="alert" className="mt-2 text-note text-u-offlimits">
          {problem}
        </p>
      )}
    </div>
  );
}

function TimeSelect({
  label,
  value,
  choices,
  disabled,
  onChange,
}: {
  label: string;
  value: string;
  choices: readonly string[];
  disabled: boolean;
  onChange: (time: string) => void;
}) {
  const offered = choices.includes(value) ? choices : [...choices, value].sort();
  return (
    <select
      aria-label={label}
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
      className="rounded-u-control border border-u-border bg-u-surface px-1.5 py-1 font-mono text-note text-u-text disabled:opacity-60"
    >
      {offered.map((time) => (
        <option key={time} value={time}>
          {time}
        </option>
      ))}
    </select>
  );
}
