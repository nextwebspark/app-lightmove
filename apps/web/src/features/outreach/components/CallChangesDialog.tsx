import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { Modal } from "../../../components/ui/Modal";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import * as poolApi from "../../candidates/api/poolApi";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as notetakerApi from "../api/notetakerApi";
import type { ApplyCallSuggestion, CallSuggestion } from "../api/notetakerApi";
import { nextPendingIndex } from "../lib/callSuggestions";
import { meetingDayOf } from "../lib/meetingTimes";
import { SUGGESTION_FORM_ID, SuggestionStep } from "./callChanges/SuggestionStep";
import { SuggestionSummary } from "./callChanges/SuggestionSummary";

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
  const [cursor, setCursor] = useState<number | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const firstPending = nextPendingIndex(suggestions, 0);
  const index = cursor ?? (firstPending >= 0 ? firstPending : 0);
  const current = suggestions[index] ?? null;
  const allDecided = asked && suggestions.length > 0 && firstPending < 0;
  const phase: "loading" | "failed" | "empty" | "step" | "summary" =
    read.isError || (ask.isError && !isRunningElsewhere && !isBillingRefusal(ask.error))
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
    }) => notetakerApi.decideCallSuggestion(projectId, candidateId, recordingId, suggestion.id, decision, body),
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
        setCursor(next.suggestions.findIndex((each) => each.id === suggestion.id));
        return;
      }
      const at = next.suggestions.findIndex((each) => each.id === suggestion.id);
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
      if (code === "NOTETAKER_SUGGESTION_DECIDED" || code === "NOTETAKER_SUGGESTION_UNAVAILABLE") {
        void read.refetch();
      }
      if (code === "PROJECT_ARCHIVED" || code === "FORBIDDEN" || code === "NOT_FOUND") onClose();
    },
  });

  const title = answer?.title ?? "Recorded call";
  const subtitle = answer ? `${title} · ${meetingDayOf(answer.startsAt)}` : undefined;

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
          onSkip={() => current && decide.mutate({ suggestion: current, decision: "skip" })}
          onClose={onClose}
          formId={SUGGESTION_FORM_ID}
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
          <div className="mt-2.5 text-subhead font-semibold">Nothing to change</div>
          <p className="mt-1 text-note text-u-text2">The call didn't state anything new for {firstName}'s record.</p>
        </div>
      )}
      {phase === "step" && current && (
        <SuggestionStep
          key={current.id + current.status}
          suggestion={current}
          index={index}
          total={suggestions.length}
          pending={decide.isPending}
          error={codeOf(decide.error) === "VALIDATION_FAILED" ? messageFor(decide.error) : null}
          onApply={(body) => decide.mutate({ suggestion: current, decision: "apply", body })}
          onSkip={() => decide.mutate({ suggestion: current, decision: "skip" })}
          onUndo={() => decide.mutate({ suggestion: current, decision: "reopen" })}
          onBack={() => setCursor(Math.max(0, index - 1))}
          onEditing={() => decide.reset()}
        />
      )}
      {phase === "summary" && answer && (
        <SuggestionSummary
          answer={answer}
          pending={decide.isPending}
          onUndo={(suggestion) => decide.mutate({ suggestion, decision: "reopen" })}
        />
      )}
    </Modal>
  );
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
      <Button variant="ghost" size="sm" disabled={index === 0 || pending} onClick={onBack}>
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
          <Button variant="secondary" size="sm" disabled={pending} onClick={onSkip}>
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
