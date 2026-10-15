import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import { PERSON_TIMELINE_KEY } from "../api/personCrmApi";
import * as scorecardApi from "../api/scorecardApi";
import type { CandidateScorecard, ScorecardCriterion, ScorecardReport } from "../api/scorecardTypes";
import { criterionKey, tallyOf, type LiveTally } from "./scorecardScoring";

export type ReportSection = keyof ScorecardReport;

const REPORT_SECTIONS: readonly ReportSection[] = [
  "careerSummary",
  "coreValue",
  "concerns",
  "workingStyle",
  "motivation",
];

interface CriterionDraft {
  score: number | null;
  evidence: string;
}

interface ScorecardValues {
  scores: Record<string, CriterionDraft>;
  report: ScorecardReport;
}

/**
 * What the consultant has changed since the read they started from, kept until it is saved. `base` is that read's
 * values, so a refused stale save can be laid over the newer one keeping only what the consultant changed.
 */
interface ScorecardDraft extends ScorecardValues {
  baseVersion: number | null;
  base: ScorecardValues;
}

const DRAFT_KEY = (candidateId: string) => `lm.scorecard-draft.${candidateId}`;

/**
 * The Assessment tab's state: the saved assessment, and the consultant's unsaved changes over it. Unsaved
 * changes are kept in this tab's session too, so closing the drawer or the page by accident loses nothing —
 * reopening the executive brings them back, marked as restored. A save names the version it started from;
 * a teammate's save, or an AI draft, in between is refused (`CANDIDATE_ASSESSMENT_STALE`) rather than overwritten.
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
  const latestDraft = useRef(draft);
  const [restored, setRestored] = useState(() => draft !== null);
  const [stale, setStale] = useState(false);

  const setDraft = (next: ScorecardDraft | null) => {
    latestDraft.current = next;
    setDraftState(next);
    storeDraft(candidateId, next);
  };

  const current = (): ScorecardDraft | null => draft ?? (saved.data ? draftOf(saved.data) : null);

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

  const assessing = useMutation({
    mutationFn: () => scorecardApi.assessWithAi(projectId, candidateId),
    onSuccess: (answer) => {
      queryClient.setQueryData(scorecardApi.SCORECARD_KEY(projectId, candidateId), answer);
      void queryClient.invalidateQueries({ queryKey: PERSON_TIMELINE_KEY(projectId, candidateId) });
      setDraft(null);
      setRestored(false);
      setStale(false);
      toast("The AI drafted what was still open — check each score before you mark it reviewed");
    },
    onError: (error) => {
      if (!isBillingRefusal(error)) toast.error(messageFor(error));
    },
  });

  /** The tab holds still while the AI drafts: an edit made now would be overwritten by its answer. */
  const change = (update: (from: ScorecardDraft) => ScorecardDraft) => {
    const from = current();
    if (from && !assessing.isPending) setDraft(update(from));
  };

  const view = current();
  const scoreOf = (key: string) => view?.scores[key]?.score ?? null;
  const tally: LiveTally | null = saved.data ? tallyOf(saved.data.panels, scoreOf) : null;
  const savedCriterion = (key: string): ScorecardCriterion | undefined =>
    saved.data?.panels
      .flatMap((panel) => panel.criteria)
      .find((criterion) => criterionKey(criterion.panel, criterion.name) === key);

  return {
    saved,
    tally,
    hasOpen: saved.data ? hasOpenFor(saved.data) : false,
    isDirty: draft !== null,
    restored,
    stale,
    isSaving: saving.isPending,
    savingReviewed: saving.isPending && saving.variables === true,
    isAssessing: assessing.isPending,
    scoreOf,
    evidenceOf: (key: string) => view?.scores[key]?.evidence ?? "",
    reportOf: (section: ReportSection) => view?.report[section] ?? "",
    /** Still the AI's untouched draft — on file, and not changed here since. */
    isAiDrafted: (key: string) => {
      const criterion = savedCriterion(key);
      const entry = view?.scores[key];
      return (
        criterion?.aiDrafted === true &&
        (entry?.score ?? null) === criterion.score &&
        (entry?.evidence ?? "") === (criterion.evidence ?? "")
      );
    },
    isReportAiDrafted: (section: ReportSection) =>
      saved.data?.reportAiDrafted.includes(section) === true &&
      (view?.report[section] ?? "") === (saved.data.report[section] ?? ""),
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
    assessWithAi: () => assessing.mutate(),
    discard: () => {
      setDraft(null);
      setRestored(false);
      setStale(false);
    },
    /** After a refused stale save: take the newer version, with what was changed here laid back over it. */
    reload: async () => {
      setStale(false);
      setRestored(false);
      const { data: fresh, isError } = await saved.refetch();
      if (isError || !fresh) {
        setStale(true);
        toast.error("The assessment could not be reloaded. Try again.");
        return;
      }
      const mine = latestDraft.current;
      setDraft(mine ? rebased(mine, fresh) : null);
    },
  };
}

export type ScorecardEditor = ReturnType<typeof useScorecardEditor>;

function valuesOf(scorecard: CandidateScorecard): ScorecardValues {
  const scores: Record<string, CriterionDraft> = {};
  for (const panel of scorecard.panels) {
    for (const criterion of panel.criteria) {
      scores[criterionKey(criterion.panel, criterion.name)] = {
        score: criterion.score,
        evidence: criterion.evidence ?? "",
      };
    }
  }
  return { scores, report: { ...scorecard.report } };
}

function draftOf(scorecard: CandidateScorecard): ScorecardDraft {
  const values = valuesOf(scorecard);
  return { ...values, baseVersion: scorecard.version, base: values };
}

/**
 * Whether Assess with AI has anything left to draft: a criterion or section nobody holds, or one still the AI's —
 * the server's own reading (`CandidateScorecard.hasOpenAmong`), so the button is never a paid no-op.
 */
function hasOpenFor(scorecard: CandidateScorecard): boolean {
  const criteria = scorecard.panels.flatMap((panel) => panel.criteria);
  return (
    criteria.some((criterion) => criterion.aiDrafted || (criterion.score === null && !criterion.evidence)) ||
    REPORT_SECTIONS.some((section) => !scorecard.report[section] || scorecard.reportAiDrafted.includes(section))
  );
}

/** The newer version with this tab's own changes on top: a change is what differs from the read it started from. */
function rebased(mine: ScorecardDraft, fresh: CandidateScorecard): ScorecardDraft | null {
  const next = draftOf(fresh);
  let changed = false;
  for (const [key, entry] of Object.entries(mine.scores)) {
    const was = mine.base.scores[key];
    if ((was && was.score === entry.score && was.evidence === entry.evidence) || !(key in next.scores)) continue;
    next.scores = { ...next.scores, [key]: entry };
    changed = true;
  }
  for (const section of REPORT_SECTIONS) {
    const text = mine.report[section];
    if ((mine.base.report[section] ?? "") === (text ?? "")) continue;
    next.report = { ...next.report, [section]: text };
    changed = true;
  }
  return changed ? next : null;
}

/** A draft kept before `base` was recorded cannot tell its changes from what it was handed, so it is let go. */
function readStoredDraft(candidateId: string): ScorecardDraft | null {
  try {
    const stored = sessionStorage.getItem(DRAFT_KEY(candidateId));
    const parsed = stored ? (JSON.parse(stored) as Partial<ScorecardDraft>) : null;
    return parsed?.base ? (parsed as ScorecardDraft) : null;
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
