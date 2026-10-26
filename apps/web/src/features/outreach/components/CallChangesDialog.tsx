import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { Modal } from "../../../components/ui/Modal";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { ApiRequestError } from "../../../lib/apiClient";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import * as poolApi from "../../candidates/api/poolApi";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as notetakerApi from "../api/notetakerApi";
import type { AppliedCallSuggestion, CallSuggestion } from "../api/notetakerApi";
import { appliedOf, defaultChoiceOf, type SuggestionChoice, withKept } from "../lib/callSuggestions";
import { meetingDayOf } from "../lib/meetingTimes";
import { DecidedSuggestions } from "./callChanges/DecidedSuggestions";
import { SuggestionRow } from "./callChanges/SuggestionRow";

type Phase = "loading" | "failed" | "empty" | "review" | "reviewed";

/**
 * Suggest changes on a recorded call (`Position.dc.html`'s "Changes from the call"): the transcript read against the
 * person's record, every change it found on one list — what the record holds now, what the call suggests, the words it
 * rests on — each ticked to keep, editable in place, and saved together by one Apply. Nothing is written before it and
 * nothing is undone after it: from then on the record is where a change is made, and the dialog reads back what each
 * change replaced and who applied it. The changes are found once the notes are filed; opening it on a call never read
 * for them reads the transcript, once.
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
  const key = notetakerApi.CALL_SUGGESTIONS_KEY(projectId, candidateId, recordingId);
  const [isRunningElsewhere, setIsRunningElsewhere] = useState(false);
  const read = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => notetakerApi.getCallSuggestions(projectId, candidateId, recordingId, signal),
    // Another tab is reading the same transcript: its answer lands here once stored.
    refetchInterval: (query) => (isRunningElsewhere && !query.state.data?.asked ? 2_000 : false),
  });
  const ask = useMutation({
    mutationFn: () => notetakerApi.suggestChanges(projectId, candidateId, recordingId),
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
  const waiting = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const decided = suggestions.filter((suggestion) => suggestion.status !== "PENDING");
  const [choices, setChoices] = useState<Record<string, SuggestionChoice>>({});
  const choiceOf = (suggestion: CallSuggestion) => choices[suggestion.id] ?? defaultChoiceOf(suggestion);
  const toApply = waiting.flatMap((suggestion) => appliedOf(suggestion, choiceOf(suggestion)) ?? []);
  const offered = waiting.filter((suggestion) => suggestion.available);

  const phase: Phase =
    read.isError || (ask.isError && !isRunningElsewhere && !isBillingRefusal(ask.error))
      ? "failed"
      : !asked
        ? "loading"
        : suggestions.length === 0
          ? "empty"
          : waiting.length === 0
            ? "reviewed"
            : "review";

  const review = useMutation({
    mutationFn: (apply: AppliedCallSuggestion[]) =>
      notetakerApi.reviewCallSuggestions(projectId, candidateId, recordingId, apply),
    onSuccess: (next, apply) => {
      queryClient.setQueryData(key, next);
      void queryClient.invalidateQueries({
        queryKey: notetakerApi.NOTETAKER_KEY(projectId, candidateId),
        exact: true,
      });
      if (apply.length > 0) {
        void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
        void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
        void queryClient.invalidateQueries({ queryKey: poolApi.PERSON_RECORD_KEY(personId) });
        toast.success(`${apply.length === 1 ? "1 change" : `${apply.length} changes`} saved to ${firstName}'s record`);
      }
      onClose();
    },
    onError: (error, apply) => {
      if (Object.keys(rowErrorsOf(error, apply)).length > 0) return;
      const code = codeOf(error);
      toast.error(messageFor(error));
      if (code === "NOTETAKER_SUGGESTION_DECIDED" || code === "NOTETAKER_SUGGESTION_UNAVAILABLE") {
        void read.refetch();
      }
      if (code === "PROJECT_ARCHIVED" || code === "FORBIDDEN" || code === "NOT_FOUND") onClose();
    },
  });
  const rowErrors = review.isError ? rowErrorsOf(review.error, review.variables ?? []) : {};

  const choose = (suggestion: CallSuggestion, next: SuggestionChoice) => {
    review.reset();
    setChoices((held) => ({ ...held, [suggestion.id]: next }));
  };
  const keepAll = (kept: boolean) => {
    review.reset();
    setChoices((held) => {
      const next = { ...held };
      for (const suggestion of offered) {
        next[suggestion.id] = withKept(suggestion, held[suggestion.id] ?? defaultChoiceOf(suggestion), kept);
      }
      return next;
    });
  };

  const title = answer?.title ?? "Recorded call";
  const subtitle = answer ? `${title} · ${meetingDayOf(answer.startsAt)}` : undefined;

  return (
    <Modal
      open
      onClose={onClose}
      title="Changes from the call"
      subtitle={subtitle}
      closeButton
      className="md:w-[760px]"
      footer={
        phase !== "reviewed" && (
          <DialogFooter
            phase={phase}
            applying={toApply.length}
            pending={review.isPending}
            canRetry={codeOf(ask.error) !== "VALIDATION_FAILED"}
            onRetry={() => {
              ask.reset();
              setIsRunningElsewhere(false);
              ask.mutate();
            }}
            onApply={() => review.mutate(toApply)}
            onClose={onClose}
          />
        )
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
          <div className="mt-2.5 text-subhead font-semibold">Nothing to change</div>
          <p className="mt-1 text-note text-u-text2">The call didn't state anything new for {firstName}'s record.</p>
        </div>
      )}
      {phase === "review" && (
        <div className="pb-1">
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 pb-2">
            <p className="text-note text-u-text2">
              <span className="font-semibold text-u-text">
                {waiting.length === 1 ? "1 change" : `${waiting.length} changes`} found
              </span>{" "}
              · {toApply.length} selected
            </p>
            {offered.length > 1 && (
              <span className="flex gap-3.5 text-meta">
                <button
                  type="button"
                  disabled={review.isPending}
                  onClick={() => keepAll(true)}
                  className="font-medium text-u-accent hover:underline disabled:opacity-60"
                >
                  Select all
                </button>
                <button
                  type="button"
                  disabled={review.isPending}
                  onClick={() => keepAll(false)}
                  className="font-medium text-u-accent hover:underline disabled:opacity-60"
                >
                  None
                </button>
              </span>
            )}
          </div>
          <ul aria-label="Changes to apply">
            {waiting.map((suggestion) => (
              <SuggestionRow
                key={suggestion.id}
                suggestion={suggestion}
                choice={choiceOf(suggestion)}
                error={rowErrors[suggestion.id] ?? null}
                disabled={review.isPending}
                onChange={(next) => choose(suggestion, next)}
              />
            ))}
          </ul>
          {decided.length > 0 && (
            <section aria-label="Already reviewed" className="mt-5">
              <h3 className="type-micro-label pb-1 font-mono text-u-text3">Already reviewed</h3>
              <DecidedSuggestions suggestions={decided} />
            </section>
          )}
        </div>
      )}
      {phase === "reviewed" && (
        <div className="pb-1">
          <p className="pb-2 text-subhead font-semibold">
            {decided.filter((suggestion) => suggestion.status === "APPLIED").length} applied{" "}
            <span className="font-normal text-u-text3">
              · {decided.filter((suggestion) => suggestion.status === "SKIPPED").length} skipped
            </span>
          </p>
          <DecidedSuggestions suggestions={decided} />
          <p className="mt-3 text-meta text-u-text3">To change any of it, edit {firstName}'s record.</p>
        </div>
      )}
    </Modal>
  );
}

/** A refused review's messages by the change they belong to: the server names its id, or its place in what was sent. */
function rowErrorsOf(error: unknown, sent: AppliedCallSuggestion[]): Record<string, string> {
  if (codeOf(error) !== "VALIDATION_FAILED" || !(error instanceof ApiRequestError)) return {};
  const byRow: Record<string, string> = {};
  for (const [key, message] of Object.entries(error.fieldErrors)) {
    const place = /^apply\[(\d+)\]/.exec(key);
    const id = place ? sent[Number(place[1])]?.id : sent.find((each) => each.id === key)?.id;
    if (id && !byRow[id]) byRow[id] = message;
  }
  return byRow;
}

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
  applying,
  pending,
  canRetry,
  onRetry,
  onApply,
  onClose,
}: {
  phase: Phase;
  applying: number;
  pending: boolean;
  canRetry: boolean;
  onRetry: () => void;
  onApply: () => void;
  onClose: () => void;
}) {
  if (phase === "loading") {
    return (
      <Button variant="secondary" onClick={onClose}>
        Cancel
      </Button>
    );
  }
  if (phase === "failed") {
    return (
      <>
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
        {canRetry && <Button onClick={onRetry}>Try again</Button>}
      </>
    );
  }
  if (phase === "empty") {
    return <Button onClick={onClose}>Close</Button>;
  }
  return (
    <div className="flex w-full items-center justify-end gap-2">
      <span className="me-auto hidden text-meta text-u-text3 sm:inline">Nothing is saved until you apply.</span>
      <Button variant="secondary" disabled={pending} onClick={onClose}>
        Not now
      </Button>
      <Button loading={pending} onClick={onApply}>
        {applying === 0 ? "Skip all" : applying === 1 ? "Apply 1 change" : `Apply ${applying} changes`}
      </Button>
    </div>
  );
}
