import { useMutation } from "@tanstack/react-query";
import { Button, FormError, useToast } from "../../../../components/ui";
import { messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../api/promptApi";
import type { FinishKind, PromptDetail, PromptTrialResult } from "../api/types";
import { versionLabel } from "../lib/promptLabels";

const FINISH_LABEL: Record<FinishKind, string> = {
  STOP: "finished normally",
  MAX_TOKENS: "cut off at the output limit",
  SAFETY: "stopped by the safety filter",
  OTHER: "stopped for another reason",
};

const whole = new Intl.NumberFormat("en-GB");

interface ScoreRow {
  id: string;
  score: number | null;
  evidence: string;
}

/** Try it: the open version, once, on the prompt's fictional input, through the feature's own call path. */
export function TrialTab({ detail, version }: { detail: PromptDetail; version: number }) {
  const toast = useToast();
  const trial = detail.trial;
  const run = useMutation({
    mutationFn: () => promptApi.tryPromptVersion(detail.prompt.id, version),
    onSuccess: () => toast(`Ran version ${version} on the synthetic input`),
  });
  // An answer belongs to the version it came from: opening another must not show it as that one's.
  const answer = run.data && run.data.version === version ? run.data : null;

  if (!trial) return null;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-u-border-strong bg-u-surface px-4 py-3.5">
        <div className="min-w-0 flex-[1_1_300px]">
          <div className="text-body font-semibold">Synthetic input · {trial.name}</div>
          <div className="mt-0.5 text-xs text-u-text2">{trial.description}</div>
        </div>
        <Button type="button" size="sm" loading={run.isPending} onClick={() => run.mutate()}>
          Run {versionLabel(version)}
        </Button>
      </div>

      {run.isError && (
        <div className="mt-4">
          <FormError message={messageFor(run.error)} />
        </div>
      )}

      {answer ? (
        <TrialAnswer answer={answer} />
      ) : (
        <p className="mx-0.5 mt-4 text-xs text-u-text3">
          The answer appears here, as the feature would read it and as the model returned it. A run counts toward the
          AI budget.
        </p>
      )}
    </div>
  );
}

function TrialAnswer({ answer }: { answer: PromptTrialResult }) {
  const scores = scoreRowsOf(answer.parsedAnswer);
  return (
    <>
      <div className="mt-4 flex flex-wrap gap-4">
        <div className="min-w-0 flex-[1_1_320px]">
          <span className="mb-1.5 block type-micro-label font-mono text-u-text3">Answer as the feature reads it</span>
          {scores ? (
            <div className="rounded-lg border border-u-border-strong bg-u-surface px-3.5 py-0.5">
              {scores.map((row) => (
                <div key={row.id} className="border-b border-u-border py-[11px] last:border-b-0">
                  <div className="flex items-baseline justify-between gap-2.5">
                    <span className="text-body font-semibold">{row.id}</span>
                    <span className="font-u-num text-body">{row.score ?? "No evidence"}</span>
                  </div>
                  <p className="mt-1 text-xs text-u-text2">{row.evidence}</p>
                </div>
              ))}
            </div>
          ) : (
            <pre className="m-0 max-h-[340px] overflow-auto whitespace-pre-wrap rounded-lg border border-u-border bg-u-sunken px-3.5 py-3 font-u-num text-xs text-u-text2">
              {answer.parsedAnswer == null
                ? "The feature could not read this answer."
                : JSON.stringify(answer.parsedAnswer, null, 2)}
            </pre>
          )}
        </div>
        <div className="min-w-0 flex-[1_1_320px]">
          <span className="mb-1.5 block type-micro-label font-mono text-u-text3">Answer as the model returned it</span>
          <pre
            aria-label="Answer as the model returned it"
            className="m-0 max-h-[340px] overflow-auto whitespace-pre-wrap rounded-lg border border-u-border bg-u-sunken px-3.5 py-3 font-u-num text-xs text-u-text2"
          >
            {answer.rawAnswer}
          </pre>
        </div>
      </div>
      <p className="mx-0.5 mt-3 text-xs text-u-text3">
        Answered in {(answer.tookMillis / 1000).toFixed(1)} s · {whole.format(answer.inputTokens)} tokens in,{" "}
        {whole.format(answer.outputTokens)} out · {FINISH_LABEL[answer.finish]}
      </p>
    </>
  );
}

/** The assessment draft's answer as its rows, when the answer has that shape. */
function scoreRowsOf(parsed: unknown): ScoreRow[] | null {
  if (typeof parsed !== "object" || parsed === null || !("scores" in parsed) || !Array.isArray(parsed.scores)) {
    return null;
  }
  const rows: ScoreRow[] = [];
  for (const entry of parsed.scores as unknown[]) {
    if (typeof entry !== "object" || entry === null || !("id" in entry) || typeof entry.id !== "string") return null;
    rows.push({
      id: entry.id,
      score: "score" in entry && typeof entry.score === "number" ? entry.score : null,
      evidence: "evidence" in entry && typeof entry.evidence === "string" ? entry.evidence : "",
    });
  }
  return rows.length > 0 ? rows : null;
}
