import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
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
  PositionTemplate,
  ReportingStructure,
} from "../api/types";
import { BriefButton } from "../components/BriefFields";
import { BriefRail } from "../components/BriefRail";
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
  undoStep,
  type ExtractionSection,
  type FillResults,
  type PositionSnapshot,
  type Receipts,
} from "../lib/documentFill";
import { addSuggestedSeat } from "../lib/orgChart";
import { STEP_PARAM, openingStepOf, stepOf, type PositionStep, type StepKey } from "../lib/steps";

const GROUND = "flex flex-1 bg-u-bg text-u-text";

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
        <div className="max-w-[440px] rounded-[11px] bg-u-surface px-5 py-4 text-[13px] leading-[1.6] text-u-text2 shadow-u-e1">
          <span className="mb-1 block text-[15px] font-semibold text-u-text">Couldn't load this brief</span>
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

  // "Read from document" state — session-only, never persisted. `receipts` is what a screen's strip and
  // a field's marker read for the snippet and Undo; a reload or a dismissed strip loses it, but the
  // `source` a fill stamped on the brief itself survives (see lib/documentFill.ts).
  const [receipts, setReceipts] = useState<Receipts>({});
  const [sectionErrors, setSectionErrors] = useState<ReadonlySet<ExtractionSection>>(new Set());
  const [suggestedTemplate, setSuggestedTemplate] = useState<PositionTemplate | null>(null);
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
    const next = { ...details, ...patch };
    setDetails(next);
    // The mandate cannot be untitled, so a blank title is held back rather than sent and refused.
    if (!next.roleTitle.trim()) return;
    detailsSave.schedule(next);
    if (immediate) void detailsSave.flush();
  };
  const changeContext = (patch: Partial<MandateContext>, immediate = false) => {
    const next = { ...context, ...patch };
    setContext(next);
    contextSave.schedule(next);
    if (immediate) void contextSave.flush();
  };
  const changeReporting = (patch: Partial<ReportingStructure>, immediate = false) => {
    const next = { ...reporting, ...patch };
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

  const dismissReceipt = (stepKey: StepKey) =>
    setReceipts((current) => {
      const next = { ...current };
      delete next[stepKey];
      return next;
    });

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
  /**
   * The Role Brief's receipt spans three draft objects (details, context and the reporting notice
   * pair), so its "Undo all" cannot be the single-object `undoStep` — it walks each field to the
   * object that owns it, exactly as the individual handlers above do.
   */
  const undoBriefAll = () => {
    const receipt = receipts.brief;
    if (!receipt) return;
    let nextDetails = details;
    let nextContext = context;
    let nextReporting = reporting;

    for (const fieldKey of Object.keys(receipt.scalars)) {
      if (fieldKey === "noticePeriod") nextReporting = undoScalar(nextReporting, fieldKey, receipt);
      else if (fieldKey === "mandateReason" || fieldKey === "businessDriver") {
        nextContext = undoScalar(nextContext, fieldKey, receipt);
      } else nextDetails = undoScalar(nextDetails, fieldKey, receipt);
    }
    for (const text of Object.keys(receipt.lists.responsibilities?.appended ?? {})) {
      nextDetails = undoListItem(nextDetails, "responsibilities", text, receipt);
    }
    for (const text of Object.keys(receipt.lists.strategicPriorities?.appended ?? {})) {
      nextContext = undoListItem(nextContext, "strategicPriorities", text, receipt);
    }

    setDetails(nextDetails);
    setContext(nextContext);
    setReporting(nextReporting);
    if (nextDetails !== details) detailsSave.schedule(nextDetails);
    if (nextContext !== context) contextSave.schedule(nextContext);
    if (nextReporting !== reporting) reportingSave.schedule(nextReporting);
    dismissReceipt("brief");
  };

  const undoTeamSize = () => {
    const receipt = receipts.reporting;
    if (!receipt) return;
    const next = undoScalar(reporting, "teamSize", receipt);
    if (next === reporting) return;
    setReporting(next);
    reportingSave.schedule(next);
  };
  /** The Reporting screen drafts one object, so its own receipt undoes in one call. */
  const undoReportingAll = () => {
    const receipt = receipts.reporting;
    if (!receipt) return;
    const next = undoStep(reporting, receipt);
    setReporting(next);
    if (next !== reporting) reportingSave.schedule(next);
    dismissReceipt("reporting");
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
   * The assessment receipt never carries a scalar (technicalShare is never read), so unlike Reporting
   * this cannot lean on the single-object `undoStep` either — `criteria`, `technical` and `behavioural`
   * are three separate draft arrays here, not fields of one object.
   */
  const undoAssessmentAll = () => {
    const receipt = receipts.assessment;
    if (!receipt) return;
    let nextCriteria = criteria;
    for (const text of Object.keys(receipt.lists.criteria?.appended ?? {})) {
      nextCriteria = undoListItem({ criteria: nextCriteria }, "criteria", text, receipt).criteria;
    }
    let nextTechnicalWire = forWire(technical);
    for (const name of Object.keys(receipt.lists.technical?.appended ?? {})) {
      nextTechnicalWire = undoListItem({ technical: nextTechnicalWire }, "technical", name, receipt).technical;
    }
    let nextBehaviouralWire = forWire(behavioural);
    for (const name of Object.keys(receipt.lists.behavioural?.appended ?? {})) {
      nextBehaviouralWire = undoListItem({ behavioural: nextBehaviouralWire }, "behavioural", name, receipt).behavioural;
    }

    setCriteria(nextCriteria);
    setTechnical(identify(nextTechnicalWire));
    setBehavioural(identify(nextBehaviouralWire));
    criteriaSave.schedule(nextCriteria);
    competenciesSave.schedule({ technical: nextTechnicalWire, behavioural: nextBehaviouralWire, technicalShare });
    dismissReceipt("assessment");
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
   */
  const readDocument = useMutation({
    mutationFn: async (fileName: string) => {
      const [detailsResult, contextResult, reportingResult, assessmentResult] = await Promise.allSettled([
        positionApi.extractDetails(projectId),
        positionApi.extractContext(projectId),
        positionApi.extractReporting(projectId),
        positionApi.extractAssessment(projectId),
      ]);
      return { fileName, detailsResult, contextResult, reportingResult, assessmentResult };
    },
    onSuccess: ({ fileName, detailsResult, contextResult, reportingResult, assessmentResult }) => {
      const results: FillResults = {};
      const failed = new Set<ExtractionSection>();
      if (detailsResult.status === "fulfilled") results.details = detailsResult.value;
      else failed.add("details");
      if (contextResult.status === "fulfilled") results.context = contextResult.value;
      else failed.add("context");
      if (reportingResult.status === "fulfilled") results.reporting = reportingResult.value;
      else failed.add("reporting");
      if (assessmentResult.status === "fulfilled") results.assessment = assessmentResult.value;
      else failed.add("assessment");

      const snapshot: PositionSnapshot = {
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
      setSectionErrors(failed);
      setSuggestedTemplate(results.details?.suggestedTemplate ?? null);
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
      if (filled > 0) toast(`Read ${filled} field${filled === 1 ? "" : "s"} from ${fileName}`);
      else if (failed.size === 0) toast("Nothing new to read from this document");
    },
    onError: (error) => toast(messageFor(error)),
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

  /**
   * Applying the banner's suggested template redrafts the brief from it first — the ordinary
   * `applyTemplate` mutation, unchanged — and then re-reads the document, so the reading's own values
   * still win over whatever the template just seeded.
   */
  const applySuggestedTemplate = () => {
    if (!suggestedTemplate) return;
    const template = suggestedTemplate;
    applyTemplate.mutate(template, {
      onSuccess: () => {
        setSuggestedTemplate(null);
        if (drafted.document) startReading(drafted.document.fileName);
      },
    });
  };

  const attachDocument = useMutation({
    mutationFn: (file: File) => positionApi.attachDocument(projectId, file),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      // A new document is a different reading — nothing the last one left behind still applies.
      setReceipts({});
      setSectionErrors(new Set());
      setSuggestedTemplate(null);
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
      setSectionErrors(new Set());
      setSuggestedTemplate(null);
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

  const briefStripError =
    sectionErrors.has("details") || sectionErrors.has("context")
      ? "Couldn't read this section from the document."
      : undefined;
  const reportingStripError = sectionErrors.has("reporting")
    ? "Couldn't read this section from the document."
    : undefined;
  const assessmentStripError = sectionErrors.has("assessment")
    ? "Couldn't read this section from the document."
    : undefined;

  return (
    <div className={`${GROUND} flex-col lg:flex-row`}>
      <BriefRail
        position={drafted}
        activeKey={step.key}
        saveStatus={saveStatus}
        publishing={publish.isPending}
        readBack={readBack}
        receipts={receipts}
        onPublish={() => void publishNow()}
        onEditPosition={() => setReopened(true)}
        onSaveDraft={() => void saveDraft()}
      />

      <div className="min-w-0 flex-1">
        <div className="px-4 pb-[100px] pt-[30px] sm:px-10">
          <StepHeader step={step} action={readAction} />

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
              stripError={briefStripError}
              suggestedTemplate={suggestedTemplate}
              onChangeDetails={changeDetails}
              onChangeContext={changeContext}
              onChangeReporting={changeReporting}
              onChangeTargetDate={(isoDate) => updateTargetDate.mutate(isoDate)}
              onPickTemplate={(template) => applyTemplate.mutate(template)}
              onAttachDocument={(file) => attachDocument.mutate(file)}
              onRemoveDocument={() => removeDocument.mutate()}
              onDownloadDocument={() => downloadDocument.mutate()}
              onExtractDocument={onExtractDocument}
              onApplySuggestedTemplate={applySuggestedTemplate}
              onDismissSuggestedTemplate={() => setSuggestedTemplate(null)}
              onUndoAll={undoBriefAll}
              onDismissStrip={() => dismissReceipt("brief")}
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
              stripError={reportingStripError}
              extracting={readDocument.isPending}
              usualDirectReports={usualDirectReports}
              onChange={changeReporting}
              onExtractDocument={onExtractDocument}
              onUndoAll={undoReportingAll}
              onDismissStrip={() => dismissReceipt("reporting")}
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
              stripError={assessmentStripError}
              extracting={readDocument.isPending}
              onCriteria={changeCriteria}
              onPanel={changePanel}
              onShare={(share) => changeAssessment({ technicalShare: share })}
              onToggleLock={(id) => setLockedCompetencies((current) => toggle(current, id))}
              onReorder={reorderPanel}
              onExtractDocument={onExtractDocument}
              onUndoAll={undoAssessmentAll}
              onDismissStrip={() => dismissReceipt("assessment")}
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
        <h1 className="text-[23px] font-bold leading-[1.25] tracking-[-0.01em] sm:text-[26px]">{step.heading}</h1>
        <p className="mt-1.5 max-w-[620px] text-[14px] leading-[1.6] text-u-text2">{step.lede}</p>
      </div>
      {action}
    </div>
  );
}
