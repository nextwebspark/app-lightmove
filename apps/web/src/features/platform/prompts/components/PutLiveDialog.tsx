import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import { Button, Field, FormError, Modal, TextArea } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import {
  EVAL_RUNS_PATH,
  evalRubricPath,
  gateFailedLine,
  gateJudgeOnlyLine,
  gateNoResultLine,
  gatePassedLine,
  gateRunLine,
  isJudgeOnlyGate,
  runLabel,
} from "../../evals/lib/evalLabels";
import { JUDGE_PROMPT_ID } from "../../evals/lib/judgeLabels";
import * as promptApi from "../api/promptApi";
import type { PromotionGate, PromptDetail, PromptVersionSummary } from "../api/types";
import { versionLabel } from "../lib/promptLabels";
import { PromptPill } from "./PromptPill";

const REASON_LIMIT = 500;

const reasonSchema = (isRequired: boolean) =>
  z.object({
    reason: z
      .string()
      .trim()
      .min(isRequired ? 1 : 0, "Say why, for whoever reviews this later")
      .max(REASON_LIMIT, `At most ${REASON_LIMIT} characters`),
  });
type PutLiveValues = z.infer<ReturnType<typeof reasonSchema>>;

/**
 * Put a version live, or roll back to an earlier one: the one act here that changes what every workspace's AI
 * calls are answered with. A version that a run on the holdout cases found no worse than the live one goes live
 * unexplained; anything else, a rollback and a run that gave no result included, asks why. The server asks for a fresh two-factor code, which
 * the app's own dialog collects.
 */
export function PutLiveDialog({
  detail,
  target,
  onClose,
  onDone,
}: {
  detail: PromptDetail;
  target: PromptVersionSummary;
  onClose: () => void;
  onDone: (updated: PromptDetail) => void;
}) {
  const live = detail.versions.find((version) => version.live);
  const isRollback = target.version < detail.prompt.liveVersion;
  const gate = isRollback ? null : target.gate;
  const needsReason = isRollback || gate?.state !== "PASSED";
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isValid },
  } = useForm<PutLiveValues>({
    resolver: zodResolver(reasonSchema(needsReason)),
    mode: "onChange",
    defaultValues: { reason: "" },
  });

  const move = useMutation({
    mutationFn: (reason: string | null) => promptApi.putPromptLive(detail.prompt.id, target.version, reason),
    onSuccess: onDone,
    onError: (error) => {
      const refusedReason = error instanceof ApiRequestError ? error.fieldErrors.reason : undefined;
      if (refusedReason && needsReason) {
        setError("reason", { type: "server", message: refusedReason });
        return;
      }
      // The code dialog was closed without a code: nothing happened, and saying so again adds nothing.
      setFormError(codeOf(error) === "PLATFORM_STEP_UP_REQUIRED" ? null : messageFor(error));
    },
  });

  const submit = handleSubmit(({ reason }) => {
    setFormError(null);
    move.mutate(needsReason ? reason.trim() : null);
  });
  const targetModel = target.modelName ?? "Default model";
  const liveModel = live?.modelName ?? "Default model";

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title={isRollback ? `Roll back to ${versionLabel(target.version)}` : `Put ${versionLabel(target.version)} live`}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={move.isPending} disabled={!isValid}>
            {isRollback ? "Roll back" : "Put live"}
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        {detail.prompt.name} goes from {versionLabel(detail.prompt.liveVersion)} to {versionLabel(target.version)} for
        every workspace, within seconds.
      </p>
      {targetModel !== liveModel && (
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-u-border px-3 py-2.5 text-body">
          <span className="type-micro-label font-mono text-u-text3">Model</span>
          <span className="text-u-text2">{liveModel}</span>
          <span aria-hidden="true" className="text-u-text3">
            →
          </span>
          <span className="text-u-text">{targetModel}</span>
        </div>
      )}
      {detail.prompt.id === JUDGE_PROMPT_ID && (
        <div className="mb-3 rounded-lg border border-u-border bg-u-sunken px-3 py-2.5 text-xs text-u-text2">
          This prompt is the judge. Once its live version changes, every rubric's judged criteria are shown only,
          until the judge is checked again on that rubric's labelled pairs.
        </div>
      )}
      {!isRollback && <GateBlock gate={gate} promptName={detail.prompt.name} />}
      <FormError message={formError} />
      {needsReason && (
        <Field label={isRollback ? "Why roll back" : "Why put it live anyway"} error={errors.reason?.message}>
          <TextArea
            rows={3}
            maxLength={REASON_LIMIT}
            autoFocus
            placeholder="Recorded with your name, for whoever reviews this later"
            invalid={!!errors.reason}
            {...register("reason")}
          />
        </Field>
      )}
      {detail.aiUse && (
        <p className="text-xs text-u-text3">
          If this changes what the feature records about a person, its description in Settings → Data &amp; retention
          needs updating in the next release.
        </p>
      )}
    </Modal>
  );
}

/** What the evals say about this version against the live one: the run it rests on, or that there is none. */
function GateBlock({ gate, promptName }: { gate: PromotionGate | null; promptName: string }) {
  if (gate?.state === "PASSED") {
    return (
      <div role="status" className="mb-4 rounded-lg border border-u-border bg-u-direct-tint px-3.5 py-3">
        <div className="flex flex-wrap items-center gap-2">
          <PromptPill tone="live">No worse</PromptPill>
          <span className="text-body font-semibold">{gateRunLine(gate)}</span>
        </div>
        <p className="mt-1.5 text-xs text-u-text2">
          {isJudgeOnlyGate(gate) ? gateJudgeOnlyLine(gate) : gatePassedLine(gate)}
        </p>
      </div>
    );
  }
  if (gate?.state === "FAILED") {
    return (
      <div role="status" className="mb-4 rounded-lg border border-u-border bg-u-offlimits-tint px-3.5 py-3">
        <div className="flex flex-wrap items-center gap-2">
          <PromptPill tone="danger">Regressed</PromptPill>
          <span className="text-body font-semibold">{gateRunLine(gate)}</span>
        </div>
        <p className="mt-1.5 text-xs text-u-text2">{gateFailedLine(gate)}</p>
      </div>
    );
  }
  if (gate?.state === "NO_RESULT") {
    return (
      <div role="status" className="mb-4 rounded-lg border border-u-border bg-u-accent-tint px-3.5 py-3">
        <div className="text-body font-semibold">
          Run {runLabel(gate.runNumber ?? 0)} on the holdout cases gave no result
        </div>
        <p className="mb-2 mt-1.5 text-xs text-u-text2">{gateNoResultLine(gate, promptName)}</p>
        {gate.datasetId && (
          <Link to={evalRubricPath(gate.datasetId)} className="text-xs font-medium text-u-accent hover:underline">
            Open the rubric
          </Link>
        )}
      </div>
    );
  }
  return (
    <div role="status" className="mb-4 rounded-lg border border-u-border bg-u-accent-tint px-3.5 py-3">
      <div className="text-body font-semibold">This version has no finished run on the holdout cases</div>
      <p className="mb-2 mt-1.5 text-xs text-u-text2">
        Without one, nothing shows it is no worse than the live version.
      </p>
      <Link to={EVAL_RUNS_PATH} className="text-xs font-medium text-u-accent hover:underline">
        Run an eval first
      </Link>
    </div>
  );
}
