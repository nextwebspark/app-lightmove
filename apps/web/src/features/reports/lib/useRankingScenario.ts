import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as reportApi from "../api/reportApi";
import type { Ranking, RankingScenario } from "../api/types";
import { baselineOf, isBaseline, panelsOffHundred, type Weighting } from "./ranking";

/** What the screen holds: a weighting and its rationale, and which saved version it was started from. */
export interface ScenarioDraft extends Weighting {
  rationale: string;
  baseVersion: number | null;
}

const DRAFT_KEY = (projectId: string) => `ranking-draft:${projectId}`;

/**
 * The position's one shared scenario as this tab edits it. Unsaved moves live in the tab (and its session, so a
 * chapter switch keeps them); Save sends the version they started from, so a teammate's save in between is refused
 * rather than overwritten, and Reload keeps this tab's weighting over the fresher version.
 */
export function useRankingScenario(projectId: string, ranking: Ranking) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const baseline = baselineOf(ranking);
  const saved = savedDraftOf(ranking);
  const [draft, setDraftState] = useState<ScenarioDraft | null>(() => readStoredDraft(projectId, ranking));
  const [isStale, setStale] = useState(false);

  const setDraft = (next: ScenarioDraft | null) => {
    setDraftState(next);
    storeDraft(projectId, next);
  };
  const current = draft ?? saved;
  const isDirty = !sameDraft(current, saved);
  const atBaseline = isBaseline(ranking.criteria, baseline, current);
  const offHundred = panelsOffHundred(ranking.criteria, current.weights);

  const saving = useMutation({
    mutationFn: () =>
      reportApi.saveRankingScenario(projectId, {
        version: current.baseVersion,
        technicalShare: current.share,
        weights: current.weights,
        rationale: current.rationale.trim() ? current.rationale : null,
      }),
    onSuccess: (scenario: RankingScenario | null) => {
      queryClient.setQueryData<Ranking>(reportApi.RANKING_KEY(projectId), (held) => (held ? { ...held, scenario } : held));
      setDraft(null);
      setStale(false);
      toast(scenario ? "Scenario saved · baseline unchanged" : "Scenario cleared · back to the agreed baseline");
    },
    onError: (error) => {
      if (codeOf(error) === "RANKING_SCENARIO_STALE") {
        setStale(true);
        return;
      }
      if (codeOf(error) === "RANKING_BRIEF_CHANGED") {
        setDraft(null);
        void queryClient.invalidateQueries({ queryKey: reportApi.RANKING_KEY(projectId) });
      }
      toast.error(messageFor(error));
    },
  });

  /** Fetches the teammate's version and keeps this tab's weighting on top of it. */
  const reloadKeepingMine = async () => {
    const fresh = await queryClient.fetchQuery({
      queryKey: reportApi.RANKING_KEY(projectId),
      queryFn: ({ signal }) => reportApi.getRanking(projectId, signal),
      staleTime: 0,
    });
    setDraft({ ...current, baseVersion: fresh.scenario?.version ?? null });
    setStale(false);
  };

  return {
    baseline,
    weighting: current,
    rationale: current.rationale,
    isDirty,
    atBaseline,
    offHundred,
    isStale,
    isSaving: saving.isPending,
    change: (next: Weighting) => setDraft({ ...current, share: next.share, weights: next.weights }),
    setRationale: (rationale: string) => setDraft({ ...current, rationale }),
    reset: () => setDraft({ ...baseline, rationale: "", baseVersion: current.baseVersion }),
    save: (onSettled?: () => void) => saving.mutate(undefined, { onSettled }),
    reloadKeepingMine,
  };
}

function savedDraftOf(ranking: Ranking): ScenarioDraft {
  const scenario = ranking.scenario;
  if (!scenario) return { ...baselineOf(ranking), rationale: "", baseVersion: null };
  return {
    share: scenario.technicalShare,
    weights: scenario.weights,
    rationale: scenario.rationale ?? "",
    baseVersion: scenario.version,
  };
}

function sameDraft(a: ScenarioDraft, b: ScenarioDraft): boolean {
  const keys = new Set([...Object.keys(a.weights), ...Object.keys(b.weights)]);
  return (
    a.share === b.share &&
    a.rationale.trim() === b.rationale.trim() &&
    [...keys].every((key) => a.weights[key] === b.weights[key])
  );
}

/** A kept draft is let go once it names other competencies than the brief's: it was made against another brief. */
function readStoredDraft(projectId: string, ranking: Ranking): ScenarioDraft | null {
  try {
    const stored = sessionStorage.getItem(DRAFT_KEY(projectId));
    const parsed = stored ? (JSON.parse(stored) as ScenarioDraft) : null;
    if (!parsed || typeof parsed.share !== "number" || !parsed.weights) return null;
    const briefKeys = ranking.criteria.map((criterion) => criterion.key);
    const draftKeys = Object.keys(parsed.weights);
    const sameBrief = briefKeys.length === draftKeys.length && briefKeys.every((key) => key in parsed.weights);
    return sameBrief ? parsed : null;
  } catch {
    return null;
  }
}

function storeDraft(projectId: string, draft: ScenarioDraft | null) {
  try {
    if (draft) sessionStorage.setItem(DRAFT_KEY(projectId), JSON.stringify(draft));
    else sessionStorage.removeItem(DRAFT_KEY(projectId));
  } catch {
    // Storage refused (a private window): the draft lives as long as the page, as it would have anyway.
  }
}
