import { useEffect, useRef, useState } from "react";
import { Input, Select, TextArea } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatNumber } from "../../../../lib/format";
import { NOTICE_PERIOD_LABELS } from "../../../../lib/noticePeriod";
import type { ApplyCallSuggestion, CallSuggestion, PackageField } from "../../api/notetakerApi";
import { PACKAGE_LINES, SUGGESTION_FIELD, valueTextOf } from "../../lib/callSuggestions";

/** The id the dialog's footer submits through, since its Apply sits outside this card's form. */
export const SUGGESTION_FORM_ID = "call-change-step";

/** One change on its card: what the record holds now, what the call suggests, and the words it rests on. */
export function SuggestionStep({
  suggestion,
  index,
  total,
  pending,
  error,
  onApply,
  onSkip,
  onUndo,
  onBack,
  onEditing,
}: {
  suggestion: CallSuggestion;
  index: number;
  total: number;
  pending: boolean;
  error: string | null;
  onApply: (body?: ApplyCallSuggestion) => void;
  onSkip: () => void;
  onUndo: () => void;
  onBack: () => void;
  onEditing: () => void;
}) {
  const field = SUGGESTION_FIELD[suggestion.kind];
  const [isEditing, setIsEditing] = useState(false);
  const [text, setText] = useState(suggestion.value.text ?? "");
  const [city, setCity] = useState(suggestion.value.city ?? "");
  const [country, setCountry] = useState(suggestion.value.country ?? "");
  const [ticked, setTicked] = useState<PackageField[]>(
    PACKAGE_LINES.filter(({ field: line }) => suggestion.value[line] !== null).map(({ field: line }) => line),
  );
  const waiting = suggestion.status === "PENDING";
  const now = valueTextOf(suggestion.kind, suggestion.current);
  const canEdit =
    waiting && suggestion.available && suggestion.kind !== "OPEN_TO_RELOCATE" && suggestion.kind !== "CURRENT_PACKAGE";
  const isLong =
    suggestion.kind === "MOTIVATION" || suggestion.kind === "EXPECTED_PACKAGE" || suggestion.kind === "TASK";

  const submit = () => {
    if (!waiting || !suggestion.available || pending) return;
    if (suggestion.kind === "CURRENT_PACKAGE") {
      if (ticked.length === 0) return onSkip();
      return onApply({ packageFields: ticked });
    }
    if (!isEditing) return onApply();
    if (suggestion.kind === "LOCATION") return onApply({ value: { city: city.trim() || null, country } });
    return onApply({ value: { text } });
  };

  // Enter applies, → skips and ← goes back from anywhere but a control, where those keys belong to the control. The
  // listener is added once and reads the latest handler, so typing in an editor never re-subscribes it.
  const onKeyRef = useRef<(event: KeyboardEvent) => void>(() => {});
  onKeyRef.current = (event) => {
    const target = event.target as HTMLElement | null;
    if (target && ["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].includes(target.tagName)) return;
    if (event.key === "Enter" && waiting && !pending) {
      event.preventDefault();
      submit();
    } else if (event.key === "ArrowRight" && waiting && !pending) {
      event.preventDefault();
      onSkip();
    } else if (event.key === "ArrowLeft" && index > 0 && !pending) {
      event.preventDefault();
      onBack();
    }
  };
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => onKeyRef.current(event);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  return (
    <form
      id={SUGGESTION_FORM_ID}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="pb-2"
    >
      <div className="flex items-center gap-2.5">
        <div className="h-[3px] flex-1 overflow-hidden rounded-full bg-u-raised">
          <div className="h-full bg-u-accent transition-[width]" style={{ width: `${((index + 1) / total) * 100}%` }} />
        </div>
        <span className="font-mono text-meta text-u-text3">
          {index + 1} of {total}
        </span>
      </div>

      <div className="mt-5 flex items-baseline justify-between gap-3">
        <span className="type-micro-label font-mono text-u-text3">{field.label}</span>
        <span className="text-right text-meta text-u-text3">{field.lands}</span>
      </div>

      {suggestion.kind === "CURRENT_PACKAGE" ? (
        <PackageLines suggestion={suggestion} now={now} ticked={ticked} onTicked={setTicked} disabled={!waiting} />
      ) : (
        <div className="mt-3.5 grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1.3fr)]">
          {suggestion.kind !== "TASK" && (
            <>
              <div className="min-w-0">
                <div className="text-meta text-u-text3">Now</div>
                <div className={cn("mt-1 break-words text-body", now ? "text-u-text2" : "italic text-u-text3")}>
                  {now ?? "Not recorded"}
                </div>
              </div>
              <div aria-hidden="true" className="hidden pt-5 text-u-text3 sm:block">
                →
              </div>
            </>
          )}
          <div className={cn("min-w-0", suggestion.kind === "TASK" && "sm:col-span-3")}>
            <div className="text-meta text-u-text3">{suggestion.kind === "TASK" ? "New task" : "Suggested"}</div>
            {isEditing ? (
              <div className="mt-1">
                {suggestion.kind === "NOTICE_PERIOD" ? (
                  <Select aria-label={field.label} value={text} onChange={(event) => setText(event.target.value)}>
                    {NOTICE_PERIOD_LABELS.map((label) => (
                      <option key={label}>{label}</option>
                    ))}
                  </Select>
                ) : suggestion.kind === "LOCATION" ? (
                  <div className="flex flex-col gap-2">
                    <Input
                      aria-label="City"
                      placeholder="City"
                      value={city}
                      onChange={(event) => setCity(event.target.value)}
                    />
                    <Input
                      aria-label="Country"
                      placeholder="Country"
                      value={country}
                      onChange={(event) => setCountry(event.target.value)}
                    />
                  </div>
                ) : isLong ? (
                  <TextArea
                    aria-label={field.label}
                    rows={3}
                    value={text}
                    onChange={(event) => setText(event.target.value)}
                  />
                ) : (
                  <Input aria-label={field.label} value={text} onChange={(event) => setText(event.target.value)} />
                )}
              </div>
            ) : (
              <div className={cn("mt-1 break-words text-u-text", isLong ? "text-body" : "text-subhead font-medium")}>
                {valueTextOf(suggestion.kind, suggestion.value)}
              </div>
            )}
            {canEdit && !isEditing && (
              <button
                type="button"
                onClick={() => {
                  onEditing();
                  setIsEditing(true);
                }}
                className="mt-1.5 text-meta font-medium text-u-accent hover:underline"
              >
                Edit
              </button>
            )}
            {error && (
              <span role="alert" className="mt-1.5 block font-mono text-meta text-u-offlimits">
                {error}
              </span>
            )}
          </div>
        </div>
      )}

      {suggestion.said && (
        <div className="mt-5 flex items-baseline gap-2.5 border-t border-u-border pt-3.5 text-note text-u-text2">
          {suggestion.at && <span className="flex-none font-mono text-meta text-u-accent">{suggestion.at}</span>}
          <q className="italic">{suggestion.said}</q>
        </div>
      )}

      {!waiting && (
        <div className="mt-4 flex items-center gap-2.5 rounded-u-control bg-u-raised px-3 py-2 text-note">
          <span className={suggestion.status === "APPLIED" ? "font-medium text-u-direct" : "text-u-text3"}>
            {suggestion.status === "APPLIED" ? (suggestion.edited ? "Applied, as edited" : "Applied") : "Skipped"}
          </span>
          <span className="flex-1" />
          <button
            type="button"
            disabled={pending}
            onClick={onUndo}
            className="text-meta font-medium text-u-accent hover:underline"
          >
            Undo
          </button>
        </div>
      )}
      {waiting && !suggestion.available && (
        <p className="mt-4 rounded-u-control bg-u-raised px-3 py-2 text-note text-u-text2">
          {suggestion.kind === "OPEN_TO_RELOCATE"
            ? "Can't be applied any more: the Open to relocate tag was renamed or retired."
            : "Can't be applied any more: the package on file is in another currency. Edit it on the Compensation section."}
        </p>
      )}
    </form>
  );
}

function PackageLines({
  suggestion,
  now,
  ticked,
  onTicked,
  disabled,
}: {
  suggestion: CallSuggestion;
  now: string | null;
  ticked: PackageField[];
  onTicked: (next: PackageField[]) => void;
  disabled: boolean;
}) {
  const lines = PACKAGE_LINES.filter(({ field }) => suggestion.value[field] !== null);
  return (
    <div className="mt-2">
      <div className="text-meta text-u-text3">
        Now: <span className={now ? "text-u-text2" : "italic"}>{now ?? "Not recorded"}</span>
        {!disabled && " · untick any figure you don't want"}
      </div>
      <div className="mt-2.5 flex flex-col gap-1">
        {lines.map(({ field, label }) => {
          const on = ticked.includes(field);
          const figure = suggestion.value[field];
          return (
            <label
              key={field}
              className="grid cursor-pointer grid-cols-[auto_84px_minmax(0,1fr)] items-center gap-2.5 rounded-u-control px-2 py-1.5 hover:bg-u-raised"
            >
              <input
                type="checkbox"
                checked={on}
                disabled={disabled}
                onChange={() => onTicked(on ? ticked.filter((each) => each !== field) : [...ticked, field])}
                className="size-[15px] accent-u-accent-solid"
              />
              <span className="text-note text-u-text3">{label}</span>
              <span
                className={cn("font-mono text-body tabular-nums", on ? "text-u-text" : "text-u-text3 line-through")}
              >
                {suggestion.value.currency} {figure === null ? "" : formatNumber(figure)}
              </span>
            </label>
          );
        })}
      </div>
      {suggestion.value.working && <div className="mt-2 text-meta text-u-text3">{suggestion.value.working}</div>}
    </div>
  );
}
