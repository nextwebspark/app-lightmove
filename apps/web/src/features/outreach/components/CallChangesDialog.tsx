import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import {
  Button,
  Input,
  Select,
  TextArea,
  useToast,
} from "../../../components/ui";
import { Modal } from "../../../components/ui/Modal";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { formatNumber } from "../../../lib/format";
import { NOTICE_PERIOD_LABELS } from "../../../lib/noticePeriod";
import { isBillingRefusal } from "../../billing/lib/billingView";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import * as poolApi from "../../candidates/api/poolApi";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as notetakerApi from "../api/notetakerApi";
import type {
  ApplyCallSuggestion,
  CallSuggestion,
  CallSuggestions,
  PackageField,
} from "../api/notetakerApi";
import {
  nextPendingIndex,
  PACKAGE_LINES,
  SUGGESTION_FIELD,
  valueTextOf,
} from "../lib/callSuggestions";
import { meetingDayOf } from "../lib/meetingTimes";

type Decision = "apply" | "skip" | "reopen";

/**
 * Suggest changes on a recorded call (`Position.dc.html`'s "Changes from the call"): the transcript read against the
 * person's record, then one change per card — what the record holds now, what the call suggests, and the words it
 * rests on. Nothing is written until Apply; the summary undoes any of it. Opening it is the press that reads the
 * transcript, once; reopening reads back what was found.
 */
export function CallChangesDialog({
  projectId,
  candidateId,
  personId,
  recordingId,
  firstName,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  personId: string;
  recordingId: string;
  firstName: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = notetakerApi.CALL_SUGGESTIONS_KEY(
    projectId,
    candidateId,
    recordingId,
  );
  const [isRunningElsewhere, setIsRunningElsewhere] = useState(false);
  const read = useQuery({
    queryKey: key,
    queryFn: ({ signal }) =>
      notetakerApi.getCallSuggestions(
        projectId,
        candidateId,
        recordingId,
        signal,
      ),
    // Another tab is reading the same transcript: its answer lands here once stored.
    refetchInterval: (query) =>
      isRunningElsewhere && !query.state.data?.asked ? 2_000 : false,
  });
  const ask = useMutation({
    mutationFn: () =>
      notetakerApi.suggestChanges(projectId, candidateId, recordingId),
    onSuccess: (answer) => {
      queryClient.setQueryData(key, answer);
      // Exact: the recording's own suggestions sit under the same prefix and were just written from the answer.
      void queryClient.invalidateQueries({
        queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId),
        exact: true,
      });
    },
    onError: (error) => {
      if (codeOf(error) === "NOTETAKER_SUGGESTIONS_RUNNING") {
        setIsRunningElsewhere(true);
        return;
      }
      // The billing sheet already says why; the review has nothing to add over it.
      if (isBillingRefusal(error)) onClose();
    },
  });

  const answer = read.data;
  const asked = answer?.asked === true;
  const shouldAsk = read.isSuccess && !asked && !isRunningElsewhere;
  const askedOnce = useRef(false);
  useEffect(() => {
    if (shouldAsk && !askedOnce.current) {
      askedOnce.current = true;
      ask.mutate();
    }
  }, [shouldAsk, ask]);

  const suggestions = answer?.suggestions ?? [];
  const [cursor, setCursor] = useState<number | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const firstPending = nextPendingIndex(suggestions, 0);
  const index = cursor ?? (firstPending >= 0 ? firstPending : 0);
  const current = suggestions[index] ?? null;
  const allDecided = asked && suggestions.length > 0 && firstPending < 0;
  const phase: "loading" | "failed" | "empty" | "step" | "summary" =
    read.isError ||
    (ask.isError && !isRunningElsewhere && !isBillingRefusal(ask.error))
      ? "failed"
      : !asked
        ? "loading"
        : suggestions.length === 0
          ? "empty"
          : showSummary || (allDecided && cursor === null)
            ? "summary"
            : "step";

  const decide = useMutation({
    mutationFn: ({
      suggestion,
      decision,
      body,
    }: {
      suggestion: CallSuggestion;
      decision: Decision;
      body?: ApplyCallSuggestion;
    }) =>
      notetakerApi.decideCallSuggestion(
        projectId,
        candidateId,
        recordingId,
        suggestion.id,
        decision,
        body,
      ),
    onSuccess: (next, { suggestion, decision }) => {
      queryClient.setQueryData(key, next);
      void queryClient.invalidateQueries({
        queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId),
        exact: true,
      });
      if (decision !== "skip") {
        void queryClient.invalidateQueries({
          queryKey: CANDIDATES_KEY_PREFIX(projectId),
        });
        void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
        void queryClient.invalidateQueries({
          queryKey: poolApi.PERSON_RECORD_KEY(personId),
        });
      }
      if (decision === "reopen") {
        setShowSummary(false);
        setCursor(
          next.suggestions.findIndex((each) => each.id === suggestion.id),
        );
        return;
      }
      const at = next.suggestions.findIndex(
        (each) => each.id === suggestion.id,
      );
      const following = nextPendingIndex(next.suggestions, at + 1);
      if (following < 0) {
        setCursor(null);
        setShowSummary(true);
      } else {
        setCursor(following);
      }
    },
    onError: (error) => {
      const code = codeOf(error);
      if (code === "VALIDATION_FAILED") return;
      toast.error(messageFor(error));
      if (
        code === "NOTETAKER_SUGGESTION_DECIDED" ||
        code === "NOTETAKER_SUGGESTION_UNAVAILABLE"
      ) {
        void read.refetch();
      }
      if (
        code === "PROJECT_ARCHIVED" ||
        code === "FORBIDDEN" ||
        code === "NOT_FOUND"
      )
        onClose();
    },
  });

  const title = answer?.title ?? "Recorded call";
  const subtitle = answer
    ? `${title} · ${meetingDayOf(answer.startsAt)}`
    : undefined;

  return (
    <Modal
      open
      onClose={onClose}
      title="Changes from the call"
      subtitle={subtitle}
      closeButton
      className="md:w-[480px]"
      footer={
        <DialogFooter
          phase={phase}
          suggestion={current}
          index={index}
          pending={decide.isPending}
          canRetry={codeOf(ask.error) !== "VALIDATION_FAILED"}
          onRetry={() => {
            ask.reset();
            setIsRunningElsewhere(false);
            ask.mutate();
          }}
          onBack={() => setCursor(Math.max(0, index - 1))}
          onNext={() => {
            const following = nextPendingIndex(suggestions, index + 1);
            if (following < 0) {
              setCursor(null);
              setShowSummary(true);
            } else {
              setCursor(following);
            }
          }}
          onSkip={() =>
            current && decide.mutate({ suggestion: current, decision: "skip" })
          }
          onClose={onClose}
          formId={FORM_ID}
        />
      }
    >
      {phase === "loading" && <ReadingTranscript />}
      {phase === "failed" && (
        <p className="py-6 text-body text-u-text2" role="alert">
          {codeOf(ask.error ?? read.error) === "AI_UNAVAILABLE"
            ? "Couldn't read the transcript just now."
            : messageFor(ask.error ?? read.error)}
        </p>
      )}
      {phase === "empty" && (
        <div className="py-8 text-center">
          <Icon d={ICONS.sparkle} size={22} className="mx-auto text-u-text3" />
          <div className="mt-2.5 text-subhead font-semibold">
            Nothing to change
          </div>
          <p className="mt-1 text-note text-u-text2">
            The call didn't state anything new for {firstName}'s record.
          </p>
        </div>
      )}
      {phase === "step" && current && (
        <SuggestionStep
          key={current.id + current.status}
          suggestion={current}
          index={index}
          total={suggestions.length}
          pending={decide.isPending}
          error={
            codeOf(decide.error) === "VALIDATION_FAILED"
              ? messageFor(decide.error)
              : null
          }
          onApply={(body) =>
            decide.mutate({ suggestion: current, decision: "apply", body })
          }
          onSkip={() =>
            decide.mutate({ suggestion: current, decision: "skip" })
          }
          onUndo={() =>
            decide.mutate({ suggestion: current, decision: "reopen" })
          }
          onBack={() => setCursor(Math.max(0, index - 1))}
          onEditing={() => decide.reset()}
        />
      )}
      {phase === "summary" && answer && (
        <SuggestionSummary
          answer={answer}
          pending={decide.isPending}
          onUndo={(suggestion) =>
            decide.mutate({ suggestion, decision: "reopen" })
          }
        />
      )}
    </Modal>
  );
}

const FORM_ID = "call-change-step";

function ReadingTranscript() {
  return (
    <div className="flex flex-col gap-3 py-4" aria-live="polite">
      <p className="flex items-center gap-2 text-body text-u-text2">
        <span
          aria-hidden="true"
          className="size-3.5 animate-spin rounded-full border-2 border-u-accent-ring border-t-u-accent"
        />
        Reading the transcript…
      </p>
      <LinesSkeleton />
    </div>
  );
}

function DialogFooter({
  phase,
  suggestion,
  index,
  pending,
  canRetry,
  onRetry,
  onBack,
  onNext,
  onSkip,
  onClose,
  formId,
}: {
  phase: "loading" | "failed" | "empty" | "step" | "summary";
  suggestion: CallSuggestion | null;
  index: number;
  pending: boolean;
  canRetry: boolean;
  onRetry: () => void;
  onBack: () => void;
  onNext: () => void;
  onSkip: () => void;
  onClose: () => void;
  formId: string;
}) {
  if (phase === "loading") {
    return (
      <Button variant="secondary" size="sm" onClick={onClose}>
        Cancel
      </Button>
    );
  }
  if (phase === "failed") {
    return (
      <>
        <Button variant="secondary" size="sm" onClick={onClose}>
          Close
        </Button>
        {canRetry && (
          <Button size="sm" onClick={onRetry}>
            Try again
          </Button>
        )}
      </>
    );
  }
  if (phase !== "step" || !suggestion) {
    return (
      <Button size="sm" onClick={onClose}>
        {phase === "summary" ? "Done" : "Close"}
      </Button>
    );
  }
  const waiting = suggestion.status === "PENDING";
  return (
    <div className="flex w-full items-center gap-2">
      <Button
        variant="ghost"
        size="sm"
        disabled={index === 0 || pending}
        onClick={onBack}
      >
        ← Back
      </Button>
      <span className="flex-1" />
      {!waiting && (
        <Button size="sm" disabled={pending} onClick={onNext}>
          Next →
        </Button>
      )}
      {waiting && (
        <>
          <Button
            variant="secondary"
            size="sm"
            disabled={pending}
            onClick={onSkip}
          >
            Skip
          </Button>
          {suggestion.available && (
            <Button size="sm" type="submit" form={formId} loading={pending}>
              {suggestion.kind === "TASK"
                ? "Add task"
                : suggestion.kind === "CURRENT_PACKAGE"
                  ? "Apply ticked"
                  : "Apply"}
            </Button>
          )}
        </>
      )}
    </div>
  );
}

function SuggestionStep({
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
    PACKAGE_LINES.filter(
      ({ field: line }) => suggestion.value[line] !== null,
    ).map(({ field: line }) => line),
  );
  const waiting = suggestion.status === "PENDING";
  const now = valueTextOf(suggestion.kind, suggestion.current);
  const canEdit =
    waiting &&
    suggestion.available &&
    suggestion.kind !== "OPEN_TO_RELOCATE" &&
    suggestion.kind !== "CURRENT_PACKAGE";
  const isLong =
    suggestion.kind === "MOTIVATION" ||
    suggestion.kind === "EXPECTED_PACKAGE" ||
    suggestion.kind === "TASK";

  const submit = () => {
    if (!waiting || !suggestion.available || pending) return;
    if (suggestion.kind === "CURRENT_PACKAGE") {
      if (ticked.length === 0) return onSkip();
      return onApply({ packageFields: ticked });
    }
    if (!isEditing) return onApply();
    if (suggestion.kind === "LOCATION")
      return onApply({ value: { city: city.trim() || null, country } });
    return onApply({ value: { text } });
  };

  // Enter applies and → skips from anywhere but a field being typed in, where Enter belongs to the field.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (
        target &&
        ["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].includes(target.tagName)
      )
        return;
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
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  });

  return (
    <form
      id={FORM_ID}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="pb-2"
    >
      <div className="flex items-center gap-2.5">
        <div className="h-[3px] flex-1 overflow-hidden rounded-full bg-u-raised">
          <div
            className="h-full bg-u-accent transition-[width]"
            style={{ width: `${((index + 1) / total) * 100}%` }}
          />
        </div>
        <span className="font-mono text-meta text-u-text3">
          {index + 1} of {total}
        </span>
      </div>

      <div className="mt-5 flex items-baseline justify-between gap-3">
        <span className="type-micro-label font-mono text-u-text3">
          {field.label}
        </span>
        <span className="text-right text-meta text-u-text3">{field.lands}</span>
      </div>

      {suggestion.kind === "CURRENT_PACKAGE" ? (
        <PackageLines
          suggestion={suggestion}
          now={now}
          ticked={ticked}
          onTicked={setTicked}
          disabled={!waiting}
        />
      ) : (
        <div className="mt-3.5 grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1.3fr)]">
          {suggestion.kind !== "TASK" && (
            <>
              <div className="min-w-0">
                <div className="text-meta text-u-text3">Now</div>
                <div
                  className={cn(
                    "mt-1 break-words text-body",
                    now ? "text-u-text2" : "italic text-u-text3",
                  )}
                >
                  {now ?? "Not recorded"}
                </div>
              </div>
              <div
                aria-hidden="true"
                className="hidden pt-5 text-u-text3 sm:block"
              >
                →
              </div>
            </>
          )}
          <div
            className={cn(
              "min-w-0",
              suggestion.kind === "TASK" && "sm:col-span-3",
            )}
          >
            <div className="text-meta text-u-text3">
              {suggestion.kind === "TASK" ? "New task" : "Suggested"}
            </div>
            {isEditing ? (
              <div className="mt-1">
                {suggestion.kind === "NOTICE_PERIOD" ? (
                  <Select
                    aria-label={field.label}
                    value={text}
                    onChange={(event) => setText(event.target.value)}
                  >
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
                  <Input
                    aria-label={field.label}
                    value={text}
                    onChange={(event) => setText(event.target.value)}
                  />
                )}
              </div>
            ) : (
              <div
                className={cn(
                  "mt-1 break-words text-u-text",
                  isLong ? "text-body" : "text-subhead font-medium",
                )}
              >
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
              <span
                role="alert"
                className="mt-1.5 block font-mono text-meta text-u-offlimits"
              >
                {error}
              </span>
            )}
          </div>
        </div>
      )}

      {suggestion.said && (
        <div className="mt-5 flex items-baseline gap-2.5 border-t border-u-border pt-3.5 text-note text-u-text2">
          {suggestion.at && (
            <span className="flex-none font-mono text-meta text-u-accent">
              {suggestion.at}
            </span>
          )}
          <q className="italic">{suggestion.said}</q>
        </div>
      )}

      {!waiting && (
        <div className="mt-4 flex items-center gap-2.5 rounded-u-control bg-u-raised px-3 py-2 text-note">
          <span
            className={
              suggestion.status === "APPLIED"
                ? "font-medium text-u-direct"
                : "text-u-text3"
            }
          >
            {suggestion.status === "APPLIED"
              ? suggestion.edited
                ? "Applied, as edited"
                : "Applied"
              : "Skipped"}
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
  const lines = PACKAGE_LINES.filter(
    ({ field }) => suggestion.value[field] !== null,
  );
  return (
    <div className="mt-2">
      <div className="text-meta text-u-text3">
        Now:{" "}
        <span className={now ? "text-u-text2" : "italic"}>
          {now ?? "Not recorded"}
        </span>
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
                onChange={() =>
                  onTicked(
                    on
                      ? ticked.filter((each) => each !== field)
                      : [...ticked, field],
                  )
                }
                className="size-[15px] accent-u-accent-solid"
              />
              <span className="text-note text-u-text3">{label}</span>
              <span
                className={cn(
                  "font-mono text-body tabular-nums",
                  on ? "text-u-text" : "text-u-text3 line-through",
                )}
              >
                {suggestion.value.currency}{" "}
                {figure === null ? "" : formatNumber(figure)}
              </span>
            </label>
          );
        })}
      </div>
      {suggestion.value.working && (
        <div className="mt-2 text-meta text-u-text3">
          {suggestion.value.working}
        </div>
      )}
    </div>
  );
}

function SuggestionSummary({
  answer,
  pending,
  onUndo,
}: {
  answer: CallSuggestions;
  pending: boolean;
  onUndo: (suggestion: CallSuggestion) => void;
}) {
  const applied = answer.suggestions.filter(
    (each) => each.status === "APPLIED",
  ).length;
  const skipped = answer.suggestions.filter(
    (each) => each.status === "SKIPPED",
  ).length;
  return (
    <div className="pb-2">
      <p className="mb-3 text-subhead font-semibold">
        {applied} applied{" "}
        <span className="font-normal text-u-text3">· {skipped} skipped</span>
      </p>
      <ul>
        {answer.suggestions.map((suggestion) => (
          <li
            key={suggestion.id}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 border-t border-u-border py-2.5"
          >
            <span className="min-w-0">
              <span className="block text-meta text-u-text3">
                {SUGGESTION_FIELD[suggestion.kind].label}
              </span>
              <span
                className={cn(
                  "block truncate text-note",
                  suggestion.status === "APPLIED"
                    ? "text-u-text"
                    : "text-u-text3",
                )}
              >
                {valueTextOf(suggestion.kind, suggestion.value)}
              </span>
            </span>
            <span className="flex items-center gap-2.5 text-meta">
              {suggestion.status === "APPLIED" ? (
                <span className="font-medium text-u-direct">Applied</span>
              ) : suggestion.status === "SKIPPED" ? (
                <span className="text-u-text3">Skipped</span>
              ) : (
                <span className="text-u-text3">Waiting</span>
              )}
              {suggestion.status !== "PENDING" && (
                <button
                  type="button"
                  disabled={pending}
                  onClick={() => onUndo(suggestion)}
                  className="font-medium text-u-accent hover:underline disabled:opacity-60"
                >
                  Undo
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
