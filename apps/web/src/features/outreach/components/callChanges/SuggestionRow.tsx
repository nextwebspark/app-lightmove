import { useId } from "react";
import { Input, Select, TextArea } from "../../../../components/ui";
import { CountryField } from "../../../../components/ui/CountryField";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import { cn } from "../../../../lib/cn";
import { formatNumber } from "../../../../lib/format";
import { NOTICE_PERIOD_LABELS } from "../../../../lib/noticePeriod";
import type { CallSuggestion, PackageField } from "../../api/notetakerApi";
import {
  editOf,
  isEditable,
  PACKAGE_LINES,
  SUGGESTION_FIELD,
  type SuggestionChoice,
  type SuggestionEdit,
  valueTextOf,
  withKept,
} from "../../lib/callSuggestions";

const MAX_TEXT = 1000;
const MAX_TASK_TITLE = 200;
const MAX_CITY = 120;

/** One waiting change in the review: a tick to keep it, the record against the call, and the words it rests on. */
export function SuggestionRow({
  suggestion,
  choice,
  error,
  disabled,
  onChange,
}: {
  suggestion: CallSuggestion;
  choice: SuggestionChoice;
  error: string | null;
  disabled: boolean;
  onChange: (next: SuggestionChoice) => void;
}) {
  const field = SUGGESTION_FIELD[suggestion.kind];
  const kept = choice.kept && suggestion.available;

  return (
    <li className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 border-t border-u-border py-4">
      <div className="pt-0.5">
        <SelectionCheckbox
          checked={kept}
          disabled={disabled || !suggestion.available}
          label={`Apply ${field.label.toLowerCase()}`}
          onChange={() => onChange(withKept(suggestion, choice, !choice.kept))}
        />
      </div>
      <div className="min-w-0">
        <div className={cn("transition-opacity", !kept && "opacity-60")}>
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <span className="type-micro-label font-mono text-u-text3">{field.label}</span>
            <span className="text-meta text-u-text3">{field.lands}</span>
          </div>
          {suggestion.kind === "CURRENT_PACKAGE" ? (
            <PackageFigures suggestion={suggestion} choice={choice} disabled={disabled} onChange={onChange} />
          ) : (
            <NowAndSuggested suggestion={suggestion} choice={choice} disabled={disabled} onChange={onChange} />
          )}
          {suggestion.said && (
            <p className="mt-3 flex items-baseline gap-2.5 border-s-2 border-u-border ps-3 text-note text-u-text2">
              {suggestion.at && <span className="flex-none font-mono text-meta text-u-accent">{suggestion.at}</span>}
              <q className="italic">{suggestion.said}</q>
            </p>
          )}
        </div>
        {!suggestion.available && (
          <p className="mt-3 rounded-u-control bg-u-raised px-3 py-2 text-note text-u-text2">
            {suggestion.kind === "OPEN_TO_RELOCATE"
              ? "Can't be applied any more: the Open to relocate tag was renamed or retired."
              : "Can't be applied any more: the package on file is in another currency. Edit it on the Compensation section."}
          </p>
        )}
        {error && (
          <p role="alert" className="mt-2 text-meta text-u-offlimits">
            {error}
          </p>
        )}
      </div>
    </li>
  );
}

function NowAndSuggested({
  suggestion,
  choice,
  disabled,
  onChange,
}: {
  suggestion: CallSuggestion;
  choice: SuggestionChoice;
  disabled: boolean;
  onChange: (next: SuggestionChoice) => void;
}) {
  const isTask = suggestion.kind === "TASK";
  const isLong = suggestion.kind === "MOTIVATION" || suggestion.kind === "EXPECTED_PACKAGE" || isTask;
  const now = valueTextOf(suggestion.kind, suggestion.current);
  const label = SUGGESTION_FIELD[suggestion.kind].label;

  return (
    <div className={cn("mt-2 grid gap-x-4 gap-y-2", !isTask && "md:grid-cols-[minmax(0,1fr)_auto_minmax(0,1.4fr)]")}>
      {!isTask && (
        <>
          <div className="min-w-0">
            <div className="text-meta text-u-text3">Now</div>
            <div className={cn("mt-0.5 break-words text-body", now ? "text-u-text2" : "italic text-u-text3")}>
              {now ?? "Not recorded"}
            </div>
          </div>
          <div aria-hidden="true" className="hidden pt-5 text-u-text3 md:block">
            →
          </div>
        </>
      )}
      <div className="min-w-0">
        <div className="flex items-baseline gap-2.5">
          <span className="text-meta text-u-text3">{isTask ? "New task" : "Suggested"}</span>
          {isEditable(suggestion) && !disabled && (
            <button
              type="button"
              aria-label={choice.edit ? `Use the suggested ${label.toLowerCase()}` : `Edit ${label.toLowerCase()}`}
              onClick={() =>
                onChange(
                  choice.edit ? { ...choice, edit: null } : { ...choice, kept: true, edit: editOf(suggestion) },
                )
              }
              className="text-meta font-medium text-u-accent hover:underline"
            >
              {choice.edit ? "Use suggestion" : "Edit"}
            </button>
          )}
        </div>
        {choice.edit ? (
          <SuggestionEditor
            suggestion={suggestion}
            edit={choice.edit}
            onEdit={(edit) => onChange({ ...choice, kept: true, edit })}
          />
        ) : (
          <div className={cn("mt-0.5 break-words text-u-text", isLong ? "text-body" : "text-subhead font-medium")}>
            {valueTextOf(suggestion.kind, suggestion.value)}
          </div>
        )}
      </div>
    </div>
  );
}

function SuggestionEditor({
  suggestion,
  edit,
  onEdit,
}: {
  suggestion: CallSuggestion;
  edit: SuggestionEdit;
  onEdit: (next: SuggestionEdit) => void;
}) {
  const countryListId = useId();
  const label = SUGGESTION_FIELD[suggestion.kind].label;
  switch (suggestion.kind) {
    case "NOTICE_PERIOD":
      return (
        <Select
          aria-label={label}
          value={edit.text}
          onChange={(event) => onEdit({ ...edit, text: event.target.value })}
          className="mt-1"
        >
          {NOTICE_PERIOD_LABELS.map((option) => (
            <option key={option}>{option}</option>
          ))}
        </Select>
      );
    case "LOCATION":
      return (
        <div className="mt-1 grid gap-2 sm:grid-cols-2">
          <Input
            aria-label="City"
            placeholder="City"
            maxLength={MAX_CITY}
            value={edit.city}
            onChange={(event) => onEdit({ ...edit, city: event.target.value })}
          />
          <CountryField
            listId={countryListId}
            value={edit.country}
            placeholder="Country"
            onChange={(country) => onEdit({ ...edit, country })}
          />
        </div>
      );
    case "TASK":
      return (
        <Input
          aria-label={label}
          maxLength={MAX_TASK_TITLE}
          value={edit.text}
          onChange={(event) => onEdit({ ...edit, text: event.target.value })}
          className="mt-1"
        />
      );
    default:
      return (
        <TextArea
          aria-label={label}
          rows={3}
          maxLength={MAX_TEXT}
          value={edit.text}
          onChange={(event) => onEdit({ ...edit, text: event.target.value })}
          className="mt-1"
        />
      );
  }
}

/** A package is taken figure by figure: a figure left unticked keeps what the record holds for it. */
function PackageFigures({
  suggestion,
  choice,
  disabled,
  onChange,
}: {
  suggestion: CallSuggestion;
  choice: SuggestionChoice;
  disabled: boolean;
  onChange: (next: SuggestionChoice) => void;
}) {
  const now = valueTextOf(suggestion.kind, suggestion.current);
  const lines = PACKAGE_LINES.filter(({ field }) => suggestion.value[field] !== null);
  const toggle = (field: PackageField, on: boolean) => {
    const others = (choice.kept ? choice.packageFields : []).filter((each) => each !== field);
    const packageFields = on ? others : [...others, field];
    onChange({ ...choice, packageFields, kept: packageFields.length > 0 });
  };

  return (
    <div className="mt-2">
      <div className="text-meta text-u-text3">
        Now: <span className={now ? "text-u-text2" : "italic"}>{now ?? "Not recorded"}</span>
      </div>
      <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1.5">
        {lines.map(({ field, label }) => {
          const on = choice.kept && choice.packageFields.includes(field);
          const figure = suggestion.value[field];
          return (
            <label key={field} className="inline-flex cursor-pointer items-center gap-2">
              <SelectionCheckbox
                checked={on}
                disabled={disabled || !suggestion.available}
                label={label}
                onChange={() => toggle(field, on)}
              />
              <span className="text-note text-u-text3">{label}</span>
              <span className={cn("font-mono text-body tabular-nums", on ? "text-u-text" : "text-u-text3 line-through")}>
                {suggestion.value.currency} {figure === null ? "" : formatNumber(figure)}
              </span>
            </label>
          );
        })}
      </div>
      {suggestion.value.working && <div className="mt-1.5 text-meta text-u-text3">{suggestion.value.working}</div>}
    </div>
  );
}
