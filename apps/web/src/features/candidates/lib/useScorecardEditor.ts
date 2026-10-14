import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as scorecardApi from "../api/scorecardApi";
import type { CandidateScorecard, ScorecardReport } from "../api/scorecardTypes";
import { criterionKey, tallyOf, type LiveTally } from "./scorecardScoring";

export type ReportSection = keyof ScorecardReport;

interface CriterionDraft {
  score: number | null;
  evidence: string;
}

/** What the consultant has changed since the read they started from, kept until it is saved. */
interface ScorecardDraft {
  baseVersion: number | null;
  scores: Record<string, CriterionDraft>;
  report: ScorecardReport;
}

const DRAFT_KEY = (candidateId: string) => `lm.scorecard-draft.${candidateId}`;

/**
 * The Assessment tab's state: the saved assessment, and the consultant's unsaved changes over it. Unsaved
 * changes are kept in this tab's session too, so closing the drawer or the page by accident loses nothing —
 * reopening the executive brings them back, marked as restored. A save names the version it started from;
 * a teammate's save in between is refused (`CANDIDATE_ASSESSMENT_STALE`) rather than overwritten.
 */
export function useScorecardEditor(projectId: string, candidateId: string, enabled: boolean) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const saved = useQuery({
    queryKey: scorecardApi.SCORECARD_KEY(projectId, candidateId),
    queryFn: ({ signal }) => scorecardApi.getScorecard(projectId, candidateId, signal),
    enabled,
  });
  const [draft, setDraftState] = useState<ScorecardDraft | null>(() => readStoredDraft(candidateId));
  const [restored, setRestored] = useState(() => draft !== null);
  const [stale, setStale] = useState(false);

  const setDraft = (next: ScorecardDraft | null) => {
    setDraftState(next);
    storeDraft(candidateId, next);
  };

  const current = (): ScorecardDraft | null => draft ?? (saved.data ? draftOf(saved.data) : null);

  const change = (update: (from: ScorecardDraft) => ScorecardDraft) => {
    const from = current();
    if (from) setDraft(update(from));
  };

  const saving = useMutation({
    mutationFn: (markReviewed: boolean) => {
      const from = current();
      if (!from || !saved.data) throw new Error("The assessment has not loaded yet");
      return scorecardApi.saveScorecard(projectId, candidateId, {
        version: from.baseVersion,
        markReviewed,
        report: from.report,
        scores: saved.data.panels.flatMap((panel) =>
          panel.criteria.map((criterion) => {
            const entry = from.scores[criterionKey(criterion.panel, criterion.name)];
            return {
              panel: criterion.panel,
              name: criterion.name,
              score: entry?.score ?? null,
              evidence: entry?.evidence.trim() ? entry.evidence : null,
            };
          }),
        ),
      });
    },
    onSuccess: (answer, markReviewed) => {
      queryClient.setQueryData(scorecardApi.SCORECARD_KEY(projectId, candidateId), answer);
      setDraft(null);
      setRestored(false);
      setStale(false);
      toast(markReviewed ? "Assessment marked reviewed" : "Assessment draft saved");
    },
    onError: (error) => {
      if (codeOf(error) === "CANDIDATE_ASSESSMENT_STALE") {
        setStale(true);
        return;
      }
      toast.error(messageFor(error));
    },
  });

  const view = current();
  const scoreOf = (key: string) => view?.scores[key]?.score ?? null;
  const tally: LiveTally | null = saved.data ? tallyOf(saved.data.panels, scoreOf) : null;

  return {
    saved,
    tally,
    isDirty: draft !== null,
    restored,
    stale,
    isSaving: saving.isPending,
    savingReviewed: saving.isPending && saving.variables === true,
    scoreOf,
    evidenceOf: (key: string) => view?.scores[key]?.evidence ?? "",
    reportOf: (section: ReportSection) => view?.report[section] ?? "",
    setScore: (key: string, score: number | null) =>
      change((from) => ({
        ...from,
        scores: { ...from.scores, [key]: { evidence: from.scores[key]?.evidence ?? "", score } },
      })),
    setEvidence: (key: string, evidence: string) =>
      change((from) => ({
        ...from,
        scores: { ...from.scores, [key]: { score: from.scores[key]?.score ?? null, evidence } },
      })),
    setReport: (section: ReportSection, text: string) =>
      change((from) => ({ ...from, report: { ...from.report, [section]: text } })),
    save: (markReviewed: boolean) => saving.mutate(markReviewed),
    discard: () => {
      setDraft(null);
      setRestored(false);
      setStale(false);
    },
    /** After a refused stale save: take the teammate's version and start over from it. */
    reload: () => {
      setDraft(null);
      setRestored(false);
      setStale(false);
      void saved.refetch();
    },
  };
}

export type ScorecardEditor = ReturnType<typeof useScorecardEditor>;

function draftOf(scorecard: CandidateScorecard): ScorecardDraft {
  const scores: Record<string, CriterionDraft> = {};
  for (const panel of scorecard.panels) {
    for (const criterion of panel.criteria) {
      scores[criterionKey(criterion.panel, criterion.name)] = {
        score: criterion.score,
        evidence: criterion.evidence ?? "",
      };
    }
  }
  return { baseVersion: scorecard.version, scores, report: { ...scorecard.report } };
}

function readStoredDraft(candidateId: string): ScorecardDraft | null {
  try {
    const stored = sessionStorage.getItem(DRAFT_KEY(candidateId));
    return stored ? (JSON.parse(stored) as ScorecardDraft) : null;
  } catch {
    return null;
  }
}

function storeDraft(candidateId: string, draft: ScorecardDraft | null) {
  try {
    if (draft) sessionStorage.setItem(DRAFT_KEY(candidateId), JSON.stringify(draft));
    else sessionStorage.removeItem(DRAFT_KEY(candidateId));
  } catch {
    // Storage refused (a private window): the draft lives as long as the drawer, as it would have anyway.
  }
}
