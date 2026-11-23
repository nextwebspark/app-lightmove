import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, CardsSkeleton, FormError, useToast } from "../../../../components/ui";
import { Chip } from "../../../../components/ui/Chip";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { formatNumber } from "../../../../lib/format";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { usePlatformPageCrumb } from "../../platformCrumb";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as evalApi from "../api/evalApi";
import type { EvalCalibrationAnswer, EvalCalibrationPair, EvalCalibrationPairs, JudgePick, RubricCriterion } from "../api/types";
import { evalRubricPath, SPLIT_LABEL } from "../lib/evalLabels";
import { PICK_LABEL } from "../lib/judgeLabels";

const PICKS: readonly JudgePick[] = ["A", "TIE", "B"];

/**
 * Platform → Evals → a dataset → Label pairs: before a rubric counts, a person picks the better answer of each pair
 * on each criterion. Blind: neither which version wrote which answer nor the judge's own picks are shown.
 */
export default function EvalCalibrationPage() {
  const { datasetId = "" } = useParams();

  const { data, isPending, error } = useQuery({
    queryKey: evalApi.EVAL_PAIRS_KEY(datasetId),
    queryFn: ({ signal }) => evalApi.getRubricPairs(datasetId, signal),
    staleTime: Infinity,
  });

  usePlatformPageCrumb(data ? `${data.datasetName} / Label pairs` : null);

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;
  if (isPending) return <CardsSkeleton cards={2} />;
  if (!data) return <FormError message={error ? messageFor(error) : "These pairs could not be opened."} />;
  if (data.pairs.length === 0) {
    return (
      <div>
        <BackToRubric datasetId={datasetId} name={data.datasetName} />
        <div className="rounded-lg border border-u-border bg-u-sunken p-[18px] text-body text-u-text2">
          No pairs have been drawn for this rubric yet. Start labelling from the dataset's Rubric view.
        </div>
      </div>
    );
  }
  return <Labelling datasetId={datasetId} data={data} />;
}

function Labelling({ datasetId, data }: { datasetId: string; data: EvalCalibrationPairs }) {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { pairs, criteria } = data;
  const isWhole = (pair: EvalCalibrationPair) => criteria.every((criterion) => pair.picks[criterion.key]);
  // Opens on the first pair still to label; with every pair labelled, on the first, to review them.
  const [index, setIndex] = useState(() => {
    const first = pairs.findIndex((pair) => !isWhole(pair));
    return first === -1 ? 0 : first;
  });
  const [draft, setDraft] = useState<Record<string, JudgePick>>(() => pairs[index]?.picks ?? {});
  const [isInputOpen, setIsInputOpen] = useState(false);

  const labelled = pairs.filter(isWhole).length;
  const isDone = index >= pairs.length;
  const pair = pairs[index];

  const show = (next: number) => {
    setIndex(next);
    setDraft(pairs[next]?.picks ?? {});
    setIsInputOpen(false);
  };

  const save = useMutation({
    mutationFn: () => evalApi.labelPair(pair.id, draft),
    onSuccess: () => {
      queryClient.setQueryData(evalApi.EVAL_PAIRS_KEY(datasetId), {
        ...data,
        pairs: pairs.map((each) => (each.id === pair.id ? { ...each, picks: draft } : each)),
      });
      void queryClient.invalidateQueries({ queryKey: evalApi.EVAL_DATASET_KEY(datasetId), exact: true });
      show(index + 1);
    },
    onError: (cause) => toast.error(messageFor(cause)),
  });

  const check = useMutation({
    mutationFn: () => evalApi.checkJudge(datasetId),
    onSuccess: (updated) => {
      queryClient.setQueryData(evalApi.EVAL_DATASET_KEY(datasetId), updated);
      navigate(evalRubricPath(datasetId));
    },
    onError: (cause) => toast.error(messageFor(cause)),
  });

  const isAllPicked = criteria.every((criterion) => draft[criterion.key]);

  return (
    <div>
      <BackToRubric datasetId={datasetId} name={data.datasetName} />
      <div className="mb-4">
        <h1 className="text-title font-semibold">Label pairs</h1>
        <p className="mt-1 max-w-[760px] text-body text-u-text2">
          For each pair, pick the better answer on each criterion, as you would judge it yourself. You are not told
          which version wrote which answer, and the judge's own picks stay hidden until all{" "}
          {formatNumber(pairs.length)} pairs are labelled.
        </p>
      </div>

      <div className="mb-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2.5">
          <span className="text-sm font-semibold">
            {isDone ? `All ${formatNumber(pairs.length)} pairs are labelled` : `Pair ${index + 1} of ${pairs.length}`}
          </span>
          <span className="font-mono text-xs text-u-text3">{labelled} labelled</span>
        </div>
        <div
          role="progressbar"
          aria-label="Pairs labelled"
          aria-valuemin={0}
          aria-valuemax={pairs.length}
          aria-valuenow={labelled}
          className="mt-2 h-1.5 overflow-hidden rounded-full bg-u-border"
        >
          <div className="h-full rounded-full bg-u-accent-solid" style={{ width: `${Math.round((labelled / pairs.length) * 100)}%` }} />
        </div>
      </div>

      {isDone ? (
        <div className="rounded-lg border border-u-border bg-u-sunken p-[18px]">
          <div className="text-sm font-semibold">Next, check the judge against your labels</div>
          <p className="mt-1.5 max-w-[680px] text-body text-u-text2">
            The judge makes its own picks on the same {formatNumber(pairs.length)} pairs, in a random order for each.
            Its agreement with yours is then shown on the rubric: at {data.thresholdPercent}% or more, the judged
            criteria count. That is {formatNumber(pairs.length)} calls, counted toward the AI budget.
          </p>
          <div className="mt-3.5 flex flex-wrap gap-2">
            <Button type="button" loading={check.isPending} onClick={() => check.mutate()}>
              Check the judge
            </Button>
            <Button type="button" variant="secondary" onClick={() => show(pairs.length - 1)}>
              Review labels
            </Button>
          </div>
        </div>
      ) : (
        <>
          <div className="rounded-lg border border-u-border bg-u-sunken px-4 py-3.5">
            <div className="flex flex-wrap items-center gap-2.5">
              <span className="type-micro-label font-mono text-u-text3">The case</span>
              <span className="font-u-num text-xs text-u-text3">{pair.caseKey}</span>
              <span className="text-body font-semibold">{pair.caseName}</span>
              <PromptPill tone="plain">{SPLIT_LABEL[pair.split]}</PromptPill>
            </div>
            <button
              type="button"
              aria-expanded={isInputOpen}
              onClick={() => setIsInputOpen((open) => !open)}
              className="mt-2 text-xs font-medium text-u-accent hover:underline"
            >
              {isInputOpen ? "Hide the full input" : "Show the full input"}
            </button>
            {isInputOpen && (
              <pre
                aria-label="The case's input"
                className="mt-2.5 max-h-80 overflow-auto whitespace-pre-wrap rounded-lg border border-dashed border-u-border-strong px-3.5 py-3 font-u-num text-xs text-u-text2"
              >
                {pair.input}
              </pre>
            )}
          </div>

          <div className="mt-3.5 flex flex-wrap gap-3.5">
            <Answer name="Answer A" answer={pair.a} />
            <Answer name="Answer B" answer={pair.b} />
          </div>

          <h2 className="mb-2 mt-[22px] text-lead font-semibold text-u-text">Which answer is better?</h2>
          <div className="rounded-lg border border-u-border bg-u-sunken px-4 py-0.5">
            {criteria.map((criterion) => (
              <CriterionPick
                key={criterion.key}
                criterion={criterion}
                pick={draft[criterion.key]}
                onPick={(pick) => setDraft((picks) => ({ ...picks, [criterion.key]: pick }))}
              />
            ))}
          </div>
          <p className="mx-0.5 mt-2.5 max-w-[760px] font-mono text-xs text-u-text3">
            Pick Tie when the difference would not matter to the person reading the answer.
          </p>

          <div className="mt-[18px] flex flex-wrap items-center gap-2.5">
            {index > 0 && (
              <Button type="button" variant="secondary" onClick={() => show(index - 1)}>
                Previous
              </Button>
            )}
            <span className="flex-[1_1_200px] font-mono text-xs text-u-text3">
              Each pair is saved as you go. You can leave and come back.
            </span>
            <Button type="button" loading={save.isPending} disabled={!isAllPicked} onClick={() => save.mutate()}>
              {index === pairs.length - 1 ? "Save and finish" : "Save and next"}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

function BackToRubric({ datasetId, name }: { datasetId: string; name: string }) {
  return (
    <Link to={evalRubricPath(datasetId)} className="mb-3 inline-flex items-center gap-1.5 text-xs font-medium text-u-text3 hover:text-u-text">
      <Icon d={ICONS.back} size={13} />
      {name}
    </Link>
  );
}

function Answer({ name, answer }: { name: string; answer: EvalCalibrationAnswer }) {
  return (
    <section aria-label={name} className="min-w-0 flex-[1_1_320px] rounded-lg border border-u-border-strong bg-u-surface px-4 py-3.5">
      <h2 className="mb-1.5 text-lead font-semibold">{name}</h2>
      {answer.prose !== null ? (
        <p className="whitespace-pre-wrap border-t border-u-border py-2.5 text-body text-u-text">{answer.prose}</p>
      ) : (
        answer.readings.map((reading) => (
          <div key={reading.label} className="border-t border-u-border py-2.5">
            <div className="flex flex-wrap items-baseline justify-between gap-2.5">
              <span className="text-body font-medium">{reading.label}</span>
              <span className="font-u-num text-body font-medium text-u-text">
                {reading.score === null ? "No evidence" : formatNumber(reading.score)}
              </span>
            </div>
            {reading.text && <div className="mt-0.5 text-xs text-u-text2">{reading.text}</div>}
          </div>
        ))
      )}
    </section>
  );
}

function CriterionPick({
  criterion,
  pick,
  onPick,
}: {
  criterion: RubricCriterion;
  pick: JudgePick | undefined;
  onPick: (pick: JudgePick) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-u-border py-3 last:border-b-0">
      <div className="min-w-0 flex-[1_1_300px]">
        <div className="text-body font-medium">{criterion.name}</div>
        <div className="mt-0.5 font-mono text-xs text-u-text3">{criterion.better}</div>
      </div>
      <div role="radiogroup" aria-label={criterion.name} className="flex flex-wrap gap-1.5">
        {PICKS.map((option) => (
          <Chip key={option} role="radio" aria-checked={pick === option} selected={pick === option} onClick={() => onPick(option)}>
            {PICK_LABEL[option]}
          </Chip>
        ))}
      </div>
    </div>
  );
}
