import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate, useOutletContext, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAutosave } from "../../../lib/useAutosave";
import * as projectsApi from "../../projects/api/projectsApi";
import * as positionApi from "../api/positionApi";
import type {
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  Position,
  PositionDetails,
  PositionExtraction,
  PositionTemplate,
  ReportingStructure,
} from "../api/types";
import { BriefButton } from "../components/BriefFields";
import { BriefRail } from "../components/BriefRail";
import { DocumentReadNotice } from "../components/DocumentReadNotice";
import { AssessmentStep, type CompetencyPanelKey } from "../components/steps/AssessmentStep";
import { CompensationStep } from "../components/steps/CompensationStep";
import { ReportingStep } from "../components/steps/ReportingStep";
import { StepFooter } from "../components/StepFooter";
import { ReviewStep } from "../components/steps/ReviewStep";
import { RoleBriefStep } from "../components/steps/RoleBriefStep";
import { forWire, identify, moveRow, toggle, type IdentifiedCompetency } from "../lib/competencyRows";
import {
  fieldCountOf,
  fillBrief,
  undoListItem,
  undoScalar,
  type ExtractionSection,
  type FillResults,
  type PositionSnapshot,
  type Receipts,
} from "../lib/documentFill";
import { addSuggestedSeat } from "../lib/orgChart";
import {
  CONTEXT_FIELD_KEYS,
  DETAILS_FIELD_KEYS,
  isUntouched,
  markManualFrom,
  REPORTING_FIELD_KEYS,
} from "../lib/provenance";
import { loadReceipts, saveReceipts } from "../lib/receiptStore";
import { STEP_PARAM, openingStepOf, stepOf, type PositionStep, type StepKey } from "../lib/steps";

const GROUND = "flex flex-1 bg-u-bg text-u-text";

const EXTRACTION_SECTIONS: readonly ExtractionSection[] = ["details", "context", "reporting", "assessment"];

const SECTION_NAMES: Record<ExtractionSection, string> = {
  details: "the role brief",
  context: "the mandate context",
  reporting: "the reporting line",
  assessment: "the assessment",
};

/** The Position tab: loads the brief, then hands the editor a snapshot to draft against. */
export function PositionPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: position, isPending, isError } = useQuery({
    queryKey: positionApi.POSITION_KEY(project.id),
    queryFn: ({ signal }) => positionApi.getPosition(project.id, signal),
  });

  if (isPending) {
    return (
      <div className={`${GROUND} justify-center pt-24 text-u-text3`}>
        <Spinner />
      </div>
    );
  }

  // A refused read must not fall through to an editor: a blank brief drawn for a 403 reads as a
  // mandate nobody has briefed, and its first keystroke would try to write it.
  if (isError) {
    return (
      <div className={`${GROUND} items-start justify-center px-4 pt-16`}>
        <div className="max-w-[440px] rounded-[11px] bg-u-surface px-5 py-4 text-body text-u-text2 shadow-u-e1">
          <span className="mb-1 block text-lead font-semibold text-u-text">Couldn't load this brief</span>
          You may no longer have access to this mandate, or the request failed. Reload the page, and ask the
          project lead if it keeps happening.
        </div>
      </div>
    );
  }

  return <PositionBrief key={project.id} projectId={project.id} position={position} />;
}

/**
 * The brief editor (claude-design/position): five steps behind a rail, the step kept in the URL.
 *
 * There is no Save button. Each section's draft autosaves as a snapshot PUT of that section alone, and
 * the write answers with the whole brief, so the cache always holds a complete document rather than
 * something stitched together client-side. "Save draft" flushes whatever is pending — a way to stop
 * waiting out the debounce, not a second way to save.
 *
 * Six channels for five screens: the Role Brief edits the details, the mandate context and the
 * notice period at once, each through its own write, so one screen never rewrites another's row.
 */
function PositionBrief({ projectId, position }: { projectId: string; position: Position }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const key = positionApi.POSITION_KEY(projectId);
  const [searchParams] = useSearchParams();
  const [openedOn] = useState(() => openingStepOf(position));
  const step = stepOf(searchParams.get(STEP_PARAM), openedOn);

  /**
   * A published brief reads back until someone says they are editing it — and publishing is how they
   * say they are done, which closes it back up. Opening any step but the review is the same statement
   * as pressing Edit position: those screens are live fields, and nothing there is a read-back.
   */
  const [reopened, setReopened] = useState(false);
  useEffect(() => {
    if (step.key !== "review") setReopened(true);
  }, [step.key]);

  const [details, setDetails] = useState<PositionDetails>(position.details);
  const [context, setContext] = useState<MandateContext>(position.context);
  const [reporting, setReporting] = useState<ReportingStructure>(position.reporting);
  const [compensation, setCompensation] = useState<Compensation>(position.compensation);
  const [criteria, setCriteria] = useState<Criterion[]>(position.assessment.criteria);
  // Carry a client-side id per competency: a lock has to survive its row moving, and the sortable
  // list needs a stable key. Stripped again on the way to the API — see lib/competencyRows.
  const [technical, setTechnical] = useState<IdentifiedCompetency[]>(() =>
    identify(position.assessment.technical),
  );
  const [behavioural, setBehavioural] = useState<IdentifiedCompetency[]>(() =>
    identify(position.assessment.behavioural),
  );
  const [technicalShare, setTechnicalShare] = useState(position.assessment.technicalShare);
  // Locks live here rather than in the table: step panels unmount when you visit another step, which
  // is exactly when somebody would have left one set.
  const [lockedCompetencies, setLockedCompetencies] = useState<ReadonlySet<string>>(new Set());

  // "Read from document" state — the tab's, never the database's. `receipts` is what a field's marker
  // reads for the document name, the snippet and the Undo; `lib/receiptStore.ts`
  // keeps it for the tab, so a reload reads back the same popover rather than a sparkle with nothing
  // behind it. The `source` a fill stamped on the brief is the half that is persisted, and the only
  // half that outlives the tab (see lib/documentFill.ts).
  const [receipts, setReceipts] = useState<Receipts>(() =>
    loadReceipts(projectId, position.document?.fileName),
  );
  const documentName = position.document?.fileName;
  useEffect(() => {
    saveReceipts(projectId, documentName, receipts);
  }, [projectId, documentName, receipts]);
  /** Set only when a reading went wrong — a reading that worked says nothing beyond its toast. */
  const [readProblem, setReadProblem] = useState<string | null>(null);
  const draftedRef = useRef<Position>(position);
  const [usualDirectReports, setUsualDirectReports] = useState<string[] | null>(null);

  // The picker's options. A failed read leaves the type-ahead with nothing to offer, which is the
  // right degradation: the title is free text and stays typeable.
  const { data: templates = [] } = useQuery({
    queryKey: positionApi.POSITION_TEMPLATES_KEY,
    queryFn: ({ signal }) => positionApi.listTemplates(signal),
    staleTime: 5 * 60 * 1000,
  });

  /** Shared persistence shape: cache the returned snapshot and toast failures. */
  const persist =
    <T,>(call: (payload: T) => Promise<Position>, onSaved?: () => void) =>
    async (payload: T) => {
      try {
        queryClient.setQueryData(key, await call(payload));
        onSaved?.();
      } catch (error) {
        toast(messageFor(error));
        throw error;
      }
    };

  const detailsSave = useAutosave(
    // The Role Brief writes the mandate's own role title, so the projects list's Role column goes stale.
    persist((next: PositionDetails) => positionApi.putDetails(projectId, next), () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
    }),
  );
  const contextSave = useAutosave(
    persist((next: MandateContext) => positionApi.putContext(projectId, next)),
  );
  const reportingSave = useAutosave(
    persist((next: ReportingStructure) => positionApi.putReporting(projectId, next)),
  );
  const compensationSave = useAutosave(
    persist((next: Compensation) => positionApi.putCompensation(projectId, next)),
  );
  const criteriaSave = useAutosave(
    persist((next: Criterion[]) => positionApi.putCriteria(projectId, next)),
  );
  const competenciesSave = useAutosave(
    persist((panels: { technical: Competency[]; behavioural: Competency[]; technicalShare: number }) =>
      positionApi.putCompetencies(projectId, panels.technical, panels.behavioural, panels.technicalShare),
    ),
  );

  const channels = [detailsSave, contextSave, reportingSave, compensationSave, criteriaSave, competenciesSave];
  const statuses = channels.map((channel) => channel.status);
  const saveStatus = statuses.includes("saving") ? "saving" : statuses.includes("saved") ? "saved" : "idle";

  /**
   * Drains every channel, one after another. `BaseEntity` carries `@Version` and every section PUT
   * rewrites the same row, so two flushes racing each other is an optimistic-lock 409 — the hook
   * serialises within a channel only.
   */
  const flushAll = async () => {
    for (const channel of channels) {
      await channel.flush();
    }
  };

  /**
   * The narrower drain a document fill uses: only the channels the screens `fillBrief` actually
   * touched, in the same fixed order as `flushAll` — so `PUT /context` still never races ahead of
   * `PUT /details`, but a fill that only touched assessment does not also flush an untouched
   * compensation channel with nothing pending (a no-op `flush()`, but still a call worth skipping).
   */
  const flushDocumentFill = async (changed: ReadonlySet<StepKey>) => {
    const toFlush = new Set<(typeof channels)[number]>();
    if (changed.has("brief")) {
      toFlush.add(detailsSave);
      toFlush.add(contextSave);
      toFlush.add(reportingSave);
    }
    if (changed.has("reporting")) toFlush.add(reportingSave);
    if (changed.has("assessment")) {
      toFlush.add(criteriaSave);
      toFlush.add(competenciesSave);
    }
    for (const channel of channels) {
      if (toFlush.has(channel)) await channel.flush();
    }
  };

  const changeDetails = (patch: Partial<PositionDetails>, immediate = false) => {
    const next = {
      ...details,
      ...patch,
      fieldSources: markManualFrom(details.fieldSources, patch, DETAILS_FIELD_KEYS),
    };
    setDetails(next);
    // The mandate cannot be untitled, so a blank title is held back rather than sent and refused.
    if (!next.roleTitle.trim()) return;
    detailsSave.schedule(next);
    if (immediate) void detailsSave.flush();
  };
  const changeContext = (patch: Partial<MandateContext>, immediate = false) => {
    const next = {
      ...context,
      ...patch,
      fieldSources: markManualFrom(context.fieldSources, patch, CONTEXT_FIELD_KEYS),
    };
    setContext(next);
    contextSave.schedule(next);
    if (immediate) void contextSave.flush();
  };
  const changeReporting = (patch: Partial<ReportingStructure>, immediate = false) => {
    const next = {
      ...reporting,
      ...patch,
      fieldSources: markManualFrom(reporting.fieldSources, patch, REPORTING_FIELD_KEYS),
    };
    setReporting(next);
    reportingSave.schedule(next);
    if (immediate) void reportingSave.flush();
  };
  const changeCompensation = (patch: Partial<Compensation>, immediate = false) => {
    const next = { ...compensation, ...patch };
    setCompensation(next);
    compensationSave.schedule(next);
    if (immediate) void compensationSave.flush();
  };
  const changeCriteria = (next: Criterion[]) => {
    setCriteria(next);
    criteriaSave.schedule(next);
  };

  /**
   * The one place the assessment's weighting is ever written, so two panels and the split can be
   * updated in one handler without any write reading another's stale, pre-update value from this
   * closure — the hazard three separate setters fired from separate calls run straight into.
   */
  const changeAssessment = (
    next: { technical?: IdentifiedCompetency[]; behavioural?: IdentifiedCompetency[]; technicalShare?: number },
    immediate = false,
  ) => {
    const nextTechnical = next.technical ?? technical;
    const nextBehavioural = next.behavioural ?? behavioural;
    const nextShare = next.technicalShare ?? technicalShare;
    setTechnical(nextTechnical);
    setBehavioural(nextBehavioural);
    setTechnicalShare(nextShare);
    competenciesSave.schedule({
      technical: forWire(nextTechnical),
      behavioural: forWire(nextBehavioural),
      technicalShare: nextShare,
    });
    if (immediate) void competenciesSave.flush();
  };
  const changePanel = (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) =>
    changeAssessment(panel === "technical" ? { technical: rows } : { behavioural: rows });
  /** Reordering is the ranking, and a decision rather than typing — so it saves at once. */
  const reorderPanel = (panel: CompetencyPanelKey) => (fromId: string, toId: string) => {
    const rows = moveRow(panel === "technical" ? technical : behavioural, fromId, toId);
    changeAssessment(panel === "technical" ? { technical: rows } : { behavioural: rows }, true);
  };

  /**
   * Replaces every section's draft with a brief the server has just rewritten. Each section holds its
   * own local copy, so a write that changes all of them — only applying a template does — has to
   * reseat all of them. Skip one and its next autosave would put the old draft back over the new brief.
   */
  const adoptBrief = (brief: Position) => {
    queryClient.setQueryData(key, brief);
    setDetails(brief.details);
    setContext(brief.context);
    setReporting(brief.reporting);
    setCompensation(brief.compensation);
    setCriteria(brief.assessment.criteria);
    setTechnical(identify(brief.assessment.technical));
    setBehavioural(identify(brief.assessment.behavioural));
    setTechnicalShare(brief.assessment.technicalShare);
    setLockedCompetencies(new Set());
  };

  // -----------------------------------------------------------------------------------------------
  // Undo — reverses one field a document reading filled, through lib/documentFill.ts's pure undo
  // functions. Each handler is a no-op once nothing is left to undo: undoScalar and undoListItem both
  // guard on the field still reading DOCUMENT / still being in the receipt, so a stale popover calling
  // one twice, or after somebody has since typed over the field, changes nothing.
  // -----------------------------------------------------------------------------------------------

  const undoDetailField = (fieldKey: string) => {
    const receipt = receipts.brief;
    if (!receipt) return;
    const next = undoScalar(details, fieldKey, receipt);
    if (next === details) return;
    setDetails(next);
    detailsSave.schedule(next);
  };
  const undoContextField = (fieldKey: string) => {
    const receipt = receipts.brief;
    if (!receipt) return;
    const next = undoScalar(context, fieldKey, receipt);
    if (next === context) return;
    setContext(next);
    contextSave.schedule(next);
  };
  const undoNoticePeriod = () => {
    const receipt = receipts.brief;
    if (!receipt) return;
    const next = undoScalar(reporting, "noticePeriod", receipt);
    if (next === reporting) return;
    setReporting(next);
    reportingSave.schedule(next);
  };
  const undoResponsibility = (text: string) => {
    const receipt = receipts.brief;
    if (!receipt) return;
    const next = undoListItem(details, "responsibilities", text, receipt);
    setDetails(next);
    detailsSave.schedule(next);
  };
  const undoTeamSize = () => {
    const receipt = receipts.reporting;
    if (!receipt) return;
    const next = undoScalar(reporting, "teamSize", receipt);
    if (next === reporting) return;
    setReporting(next);
    reportingSave.schedule(next);
  };
  const undoCriterion = (text: string) => {
    const receipt = receipts.assessment;
    if (!receipt) return;
    const next = undoListItem({ criteria }, "criteria", text, receipt).criteria;
    setCriteria(next);
    criteriaSave.schedule(next);
  };
  const undoCompetency = (panel: CompetencyPanelKey, name: string) => {
    const receipt = receipts.assessment;
    if (!receipt) return;
    if (panel === "technical") {
      const nextWire = undoListItem({ technical: forWire(technical) }, "technical", name, receipt).technical;
      const next = identify(nextWire);
      setTechnical(next);
      competenciesSave.schedule({ technical: nextWire, behavioural: forWire(behavioural), technicalShare });
    } else {
      const nextWire = undoListItem({ behavioural: forWire(behavioural) }, "behavioural", name, receipt).behavioural;
      const next = identify(nextWire);
      setBehavioural(next);
      competenciesSave.schedule({ technical: forWire(technical), behavioural: nextWire, technicalShare });
    }
  };
  /**
   * Draft this brief as the picked role, and take its title while we are at it. Pending edits go
   * first: a title still inside the autosave debounce would otherwise land after the redraft and
   * reinstate the section it replaced. The title is then written through the ordinary details save
   * rather than by the template — the server keeps the two apart deliberately.
   */
  const applyTemplate = useMutation({
    mutationFn: async (template: PositionTemplate) => {
      await flushAll();
      const drafted = await positionApi.applyTemplate(projectId, template.id);
      const titled = { ...drafted.details, roleTitle: template.title };
      return { brief: { ...drafted, details: titled }, titled };
    },
    onSuccess: ({ brief, titled }, template) => {
      adoptBrief(brief);
      detailsSave.schedule(titled);
      void detailsSave.flush();
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast(`Brief drafted from the ${template.title} template.`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const publish = useMutation({
    mutationFn: async () => {
      await flushAll();
      return positionApi.publish(projectId);
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      setReopened(false);
      toast("Position profile published");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const withdraw = useMutation({
    mutationFn: () => positionApi.withdrawPublication(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      toast("Publication withdrawn");
    },
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * Publishing a brief that is already published. The stamp does not move — it records when the
   * brief was first called ready — so what this does is flush what the edits left in flight. It is
   * the same act from where the consultant sits: they said it was ready, and they are saying it again.
   */
  const publishNow = async () => {
    if (!position.publication.publishedAt) {
      publish.mutate();
      return;
    }
    await flushAll();
    setReopened(false);
    toast("Changes published");
  };

  const saveDraft = async () => {
    await flushAll();
    toast("Draft saved");
  };

  /** Where a published brief leads: the mandate's own market, which is the next thing to be done. */
  const goToStrategy = () => navigate(`/projects/${projectId}/strategy`);

  /**
   * "Read from document": the four section reads, fanned out and settled independently — one section
   * failing must leave the other three filled, per epic #393 — then folded into the brief in one pass
   * by `fillBrief`. `mutate`'s variable is the file name a receipt is stamped with, not read back off
   * `position`/`drafted`: the prop is a stale closure at the moment an attach's `onSuccess` fires this.
   *
   * <p>When the reading names a template and nobody has typed into the brief, the template is applied
   * first and the same reading is then folded over the redrafted brief — so the document's values still
   * win over what the template seeded, without paying for the four reads twice. The check reads the
   * latest draft through a ref, not this closure: the reads take seconds, and somebody may type meanwhile.
   * The title comes from the document when it states one, not from the template.
   */
  const readDocument = useMutation({
    mutationFn: async (fileName: string) => {
      const [detailsResult, contextResult, reportingResult, assessmentResult] = await Promise.allSettled([
        positionApi.extractDetails(projectId),
        positionApi.extractContext(projectId),
        positionApi.extractReporting(projectId),
        positionApi.extractAssessment(projectId),
      ]);
      const suggested = detailsResult.status === "fulfilled" ? detailsResult.value.suggestedTemplate : null;
      let redrafted: { brief: Position; template: PositionTemplate } | null = null;
      if (suggested && isUntouched(draftedRef.current)) {
        try {
          await flushAll();
          redrafted = { brief: await positionApi.applyTemplate(projectId, suggested.id), template: suggested };
        } catch {
          // The template is a bonus on top of the reading; failing to apply it must not lose the reading.
        }
      }
      return { fileName, redrafted, detailsResult, contextResult, reportingResult, assessmentResult };
    },
    onSuccess: ({ fileName, redrafted, detailsResult, contextResult, reportingResult, assessmentResult }) => {
      const settled = { details: detailsResult, context: contextResult, reporting: reportingResult, assessment: assessmentResult };
      const results: FillResults = {};
      for (const section of EXTRACTION_SECTIONS) {
        const result = settled[section];
        if (result.status === "fulfilled") results[section] = result.value;
      }

      if (redrafted) adoptBrief(redrafted.brief);
      const base = redrafted?.brief;
      const snapshot: PositionSnapshot = base
        ? {
            details: base.details,
            context: base.context,
            reporting: base.reporting,
            criteria: base.assessment.criteria,
            technical: base.assessment.technical,
            behavioural: base.assessment.behavioural,
            technicalShare: base.assessment.technicalShare,
          }
        : {
            details,
            context,
            reporting,
            criteria,
            technical: forWire(technical),
            behavioural: forWire(behavioural),
            technicalShare,
          };
      const outcome = fillBrief(snapshot, results, fileName);

      setDetails(outcome.next.details);
      setContext(outcome.next.context);
      setReporting(outcome.next.reporting);
      setCriteria(outcome.next.criteria);
      setTechnical(identify(outcome.next.technical));
      setBehavioural(identify(outcome.next.behavioural));
      setReceipts((current) => ({ ...current, ...outcome.receipts }));
      setUsualDirectReports(results.reporting?.usualDirectReports ?? null);

      if (outcome.changed.has("brief") || outcome.changed.has("reporting")) {
        reportingSave.schedule(outcome.next.reporting);
      }
      if (outcome.changed.has("brief")) {
        detailsSave.schedule(outcome.next.details);
        contextSave.schedule(outcome.next.context);
      }
      if (outcome.changed.has("assessment")) {
        criteriaSave.schedule(outcome.next.criteria);
        competenciesSave.schedule({
          technical: outcome.next.technical,
          behavioural: outcome.next.behavioural,
          technicalShare: outcome.next.technicalShare,
        });
      }
      void flushDocumentFill(outcome.changed);

      const filled = Object.values(outcome.receipts).reduce((sum, receipt) => sum + fieldCountOf(receipt), 0);
      // The title carries no receipt, so `filled` never counts it — but a rename is the most visible
      // thing a reading can do, and must never be reported as "nothing new".
      const renamedTo =
        outcome.next.details.roleTitle !== snapshot.details.roleTitle ? outcome.next.details.roleTitle : null;
      const problem = readProblemOf(settled, filled > 0 || renamedTo !== null, fileName);
      setReadProblem(problem);
      if (problem) return;

      const done: string[] = [];
      if (redrafted) done.push(`drafted from the ${redrafted.template.title} template`);
      if (filled > 0) done.push(`read ${filled} field${filled === 1 ? "" : "s"} from ${fileName}`);
      if (renamedTo) done.push(`renamed the mandate "${renamedTo}"`);
      const summary = done.length > 0 ? sentenceOf(done) : null;
      // The org chart merge can decline a proposed manager or direct report with zero other signal —
      // this is the one place a reading's own success toast can still say so.
      const orgChartSkip = outcome.skipped.find((field) => field.fieldKey === "orgChart");
      if (orgChartSkip) {
        const reason =
          orgChartSkip.reason === "full"
            ? "the org chart is at its seat limit"
            : "this mandate has no seat yet";
        toast(
          summary
            ? `${summary} — some reporting lines didn't fit because ${reason}.`
            : `Reporting lines from ${fileName} didn't fit because ${reason}.`,
        );
      } else toast(summary ?? "Nothing new to read from this document");
    },
    onError: (error) => setReadProblem(messageFor(error)),
  });

  /** A read already in flight is not started again — Extract with AI, Read again and the per-step
   *  action all funnel through this one guard. */
  const startReading = (fileName: string) => {
    if (readDocument.isPending) return;
    readDocument.mutate(fileName);
  };
  const onExtractDocument = () => {
    if (drafted.document) startReading(drafted.document.fileName);
  };

  const attachDocument = useMutation({
    mutationFn: (file: File) => positionApi.attachDocument(projectId, file),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      // A new document is a different reading — nothing the last one left behind still applies.
      setReceipts({});
      setReadProblem(null);
      setUsualDirectReports(null);
      if (saved.document) startReading(saved.document.fileName);
    },
    onError: (error) => toast(messageFor(error)),
  });
  const removeDocument = useMutation({
    mutationFn: () => positionApi.removeDocument(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      setReceipts({});
      setReadProblem(null);
      setUsualDirectReports(null);
    },
    onError: (error) => toast(messageFor(error)),
  });
  const downloadDocument = useMutation({
    mutationFn: () =>
      positionApi.saveDocument(projectId, position.document?.fileName ?? "position-description"),
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * The target date is the project's, so it is written there and read back into the brief — the
   * reporting PUT strips it on the way out, and this is the one write that carries it.
   */
  const updateTargetDate = useMutation({
    mutationFn: (targetDate: string) => projectsApi.updateProject(projectId, { targetDate }),
    onSuccess: (saved) => {
      setReporting((current) => ({ ...current, targetStart: saved.targetDate }));
      queryClient.setQueryData<Position>(key, (current) =>
        current ? { ...current, reporting: { ...current.reporting, targetStart: saved.targetDate } } : current,
      );
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
    },
    onError: (error) => toast(messageFor(error)),
  });

  // The rail and the review cards read a brief, not six drafts, so the edits in flight are folded
  // over the last saved snapshot — otherwise a step reads as untouched until its debounce fires.
  const drafted: Position = {
    ...position,
    details,
    context,
    reporting,
    compensation,
    assessment: {
      criteria,
      technical: forWire(technical),
      behavioural: forWire(behavioural),
      technicalShare,
    },
  };
  useEffect(() => {
    draftedRef.current = drafted;
  });

  // Only the review reads back. The rail stands beside every step, so without the step the brief
  // would go on offering "Edit position" next to a Compensation form that is live and taking input.
  const readBack = Boolean(drafted.publication.publishedAt) && !reopened && step.key === "review";

  const readAction =
    (step.key === "reporting" || step.key === "assessment") && drafted.document ? (
      <BriefButton
        variant="outline"
        onClick={onExtractDocument}
        loading={readDocument.isPending}
        className="flex-none text-u-inferred"
      >
        <Icon d={ICONS.sparkle} size={13} />
        Read from document
      </BriefButton>
    ) : undefined;

  return (
    <div className={`${GROUND} flex-col lg:flex-row`}>
      <BriefRail
        position={drafted}
        activeKey={step.key}
        saveStatus={saveStatus}
        publishing={publish.isPending}
        readBack={readBack}
        onPublish={() => void publishNow()}
        onEditPosition={() => setReopened(true)}
        onSaveDraft={() => void saveDraft()}
      />

      <div className="min-w-0 flex-1">
        <div className="px-4 pb-[100px] pt-[30px] sm:px-10">
          <StepHeader step={step} action={readAction} />

          {readProblem && (step.key === "brief" || step.key === "reporting" || step.key === "assessment") && (
            <DocumentReadNotice
              message={readProblem}
              retrying={readDocument.isPending}
              onRetry={onExtractDocument}
              onDismiss={() => setReadProblem(null)}
            />
          )}

          {step.key === "brief" && (
            <RoleBriefStep
              details={details}
              context={context}
              reporting={reporting}
              document={drafted.document}
              templates={templates}
              applyingTemplate={applyTemplate.isPending}
              uploading={attachDocument.isPending || removeDocument.isPending}
              extracting={readDocument.isPending}
              savingTargetDate={updateTargetDate.isPending}
              receipt={receipts.brief}
              onChangeDetails={changeDetails}
              onChangeContext={changeContext}
              onChangeReporting={changeReporting}
              onChangeTargetDate={(isoDate) => updateTargetDate.mutate(isoDate)}
              onPickTemplate={(template) => applyTemplate.mutate(template)}
              onAttachDocument={(file) => attachDocument.mutate(file)}
              onRemoveDocument={() => removeDocument.mutate()}
              onDownloadDocument={() => downloadDocument.mutate()}
              onExtractDocument={onExtractDocument}
              onUndoDetail={undoDetailField}
              onUndoContext={undoContextField}
              onUndoNotice={undoNoticePeriod}
              onUndoResponsibility={undoResponsibility}
            />
          )}
          {step.key === "reporting" && (
            <ReportingStep
              roleTitle={details.roleTitle}
              seniority={details.seniority}
              reporting={reporting}
              receipt={receipts.reporting}
              usualDirectReports={usualDirectReports}
              onChange={changeReporting}
              onUndoTeamSize={undoTeamSize}
              onAddSuggestedSeat={(title) => {
                const result = addSuggestedSeat(reporting.orgChart, title);
                if (result.blocked) return;
                changeReporting({ orgChart: result.chart }, true);
              }}
            />
          )}
          {step.key === "compensation" && (
            <CompensationStep compensation={compensation} onChange={changeCompensation} />
          )}
          {step.key === "assessment" && (
            <AssessmentStep
              criteria={criteria}
              technical={technical}
              behavioural={behavioural}
              technicalShare={technicalShare}
              locked={lockedCompetencies}
              receipt={receipts.assessment}
              onCriteria={changeCriteria}
              onPanel={changePanel}
              onShare={(share) => changeAssessment({ technicalShare: share })}
              onToggleLock={(id) => setLockedCompetencies((current) => toggle(current, id))}
              onReorder={reorderPanel}
              onUndoCriterion={undoCriterion}
              onUndoCompetency={undoCompetency}
            />
          )}
          {step.key === "review" && (
            <ReviewStep position={drafted} readBack={readBack} onWithdraw={() => withdraw.mutate()} />
          )}

          <StepFooter
            activeKey={step.key}
            published={Boolean(drafted.publication.publishedAt)}
            onGoToStrategy={goToStrategy}
          />
        </div>
      </div>
    </div>
  );
}

/**
 * The step's question and a line under it. The acts are the rail's and the page foot's, not this —
 * `action` is the one exception, "Read from document" on the Reporting and Assessment steps only: the
 * Role Brief already carries its own read control on the file card, Compensation is never read, and
 * the review has nothing left to read.
 */
function StepHeader({ step, action }: { step: PositionStep; action?: ReactNode }) {
  return (
    <div className="mb-7 flex flex-wrap items-start justify-between gap-4 min-w-0">
      <div className="min-w-0">
        <h1 className="type-title">{step.heading}</h1>
        <p className="mt-1.5 max-w-[620px] text-body text-u-text2">{step.lede}</p>
      </div>
      {action}
    </div>
  );
}

/**
 * The line a reading leaves when it went wrong, or null when it worked. A rejected section is an
 * HTTP failure — an unreadable file carries its own specific sentence in `detail`. A model the server
 * could not reach is not: it answers 200 with nothing proposed (`"none"`, or `"documentHeadings"` for
 * the details step's heuristic fallback), which is told apart from a document that says nothing only
 * by every model-read section coming back that way. A reading that proposed values a person had
 * already typed over is not a problem — it read fine and had nothing new.
 */
function readProblemOf(
  settled: Record<ExtractionSection, PromiseSettledResult<PositionExtraction>>,
  changedAnything: boolean,
  fileName: string,
): string | null {
  const rejected = EXTRACTION_SECTIONS.filter((section) => settled[section].status === "rejected");
  if (rejected.length === EXTRACTION_SECTIONS.length) {
    return messageFor((settled[rejected[0]] as PromiseRejectedResult).reason);
  }
  if (rejected.length > 0) {
    const list = sentenceOf(rejected.map((section) => SECTION_NAMES[section]), false);
    return `Couldn't read ${list} from ${fileName}. ${changedAnything ? "The rest was filled." : "The rest was read, with nothing new in it."}`;
  }
  if (changedAnything) return null;

  const readings = EXTRACTION_SECTIONS.map(
    (section) => (settled[section] as PromiseFulfilledResult<PositionExtraction>).value,
  );
  if (readings.some((reading) => reading.fields.length > 0)) return null;
  if (readings.every((reading) => reading.extractionSource !== "model")) {
    return "The document reader couldn't be reached, so nothing was filled. Try again in a moment.";
  }
  return `Nothing could be read from ${fileName} — check it is the position description.`;
}

/** "a", "a and b", "a, b and c" — capitalised as a sentence unless it sits mid-line. */
function sentenceOf(parts: readonly string[], capitalise = true): string {
  const joined = parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(", ")} and ${parts.at(-1)}`;
  return capitalise ? joined.charAt(0).toUpperCase() + joined.slice(1) : joined;
}
