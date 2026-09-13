import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useAutosave } from "../../../lib/useAutosave";
import * as projectsApi from "../../projects/api/projectsApi";
import * as positionApi from "../api/positionApi";
import type {
  Benefit,
  BenefitFrequency,
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  Position,
  PositionDetails,
  PositionExtraction,
  PositionTemplate,
  ProposedField,
  ReportingStructure,
  StrategicPriority,
} from "../api/types";
import { StepNavigation } from "../components/StepNavigation";
import type { CompetencyPanelKey } from "../components/steps/AssessmentStep";
import {
  competencyFrom,
  forWire,
  identify,
  moveRow,
  PACK_SEPARATOR,
  toggle,
  type IdentifiedCompetency,
} from "../lib/competencyRows";
import { StepRail } from "../components/StepRail";
import { AssessmentStep } from "../components/steps/AssessmentStep";
import { CompensationStep } from "../components/steps/CompensationStep";
import { MandateContextStep, MandateReasonField } from "../components/steps/MandateContextStep";
import { PositionDetailsStep } from "../components/steps/PositionDetailsStep";
import { ReportingStructureStep } from "../components/steps/ReportingStructureStep";
import { ReviewStep } from "../components/steps/ReviewStep";
import { EMPLOYMENT_TYPE_LABELS } from "../lib/labels";
import { POSITION_STEPS, stepIndexOf, type StepKey } from "../lib/steps";
import { SENIORITY_TIERS } from "../../../lib/seniority";

const EMPLOYMENT_TYPES: readonly string[] = Object.keys(EMPLOYMENT_TYPE_LABELS);

/** Mirrors `PutCriteriaRequest`'s and `PutCompetenciesRequest`'s own per-brief ceilings. */
const CRITERIA_MAX_COUNT = 30;
const COMPETENCY_MAX_COUNT_PER_PANEL = 10;

interface AssessmentAccumulator {
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
}

function isEmploymentType(value: string): value is NonNullable<PositionDetails["employmentType"]> {
  return EMPLOYMENT_TYPES.includes(value);
}

function isSeniority(value: string): value is NonNullable<PositionDetails["seniority"]> {
  return (SENIORITY_TIERS as readonly string[]).includes(value);
}

/** The Position tab: loads the brief, then hands the wizard a snapshot to draft against. */
export function PositionPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: position } = useQuery({
    queryKey: positionApi.POSITION_KEY(project.id),
    queryFn: ({ signal }) => positionApi.getPosition(project.id, signal),
  });

  if (!position) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  return <PositionWizard key={project.id} projectId={project.id} position={position} />;
}

/**
 * The brief editor (Position.dc.html): six steps, a summary rail and a Back/Next footer.
 *
 * There is no Save button. Each step's draft autosaves as a snapshot PUT of that step alone, and the
 * write answers with the whole brief, so the cache always holds a complete document rather than
 * something stitched together client-side. "Save draft" flushes whatever is pending — it is a way to
 * stop waiting out the debounce, not a second way to save.
 *
 * The step in view is local state rather than a route: the mockup has no per-step URL, and a step is
 * a place in a form rather than a resource anyone would link to.
 */
function PositionWizard({ projectId, position }: { projectId: string; position: Position }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();
  const key = positionApi.POSITION_KEY(projectId);

  // A published brief opens on its own review: somebody coming back to a finished mandate — its
  // author, a colleague, the same person after a logout — is looking at what was published rather
  // than starting the wizard again.
  const opensOn: StepKey = position.publication.publishedAt ? "review" : "details";
  const [currentStep, setCurrentStep] = useState<StepKey>(opensOn);
  // Somebody has said they mean to change a published brief. It opens the review's section links
  // rather than unlocking anything: nothing here was ever locked.
  const [editingPublished, setEditingPublished] = useState(false);
  // The furthest step reached this sitting, which is what the rail is allowed to call done — see
  // doneSteps in lib/steps, which reads publication first.
  const [furthestStep, setFurthestStep] = useState<StepKey>(opensOn);
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
  // Locks live here rather than in the panel: step panels unmount when you visit another step, which
  // is exactly when somebody would have left one set.
  const [lockedCompetencies, setLockedCompetencies] = useState<ReadonlySet<string>>(new Set());
  // "Read from document"'s proposals. Local state, never the query cache: a proposal is a transient
  // read, not a fact about the mandate, and nothing here is written until a row is accepted.
  // One slot per step, not one shared slot: a reading on step two must survive visiting step four
  // and back, the same reason contextSave/compensationSave are already separate autosave channels.
  const [detailsExtraction, setDetailsExtraction] = useState<PositionExtraction | null>(null);
  const [contextExtraction, setContextExtraction] = useState<PositionExtraction | null>(null);
  const [compensationExtraction, setCompensationExtraction] = useState<PositionExtraction | null>(null);
  const [assessmentExtraction, setAssessmentExtraction] = useState<PositionExtraction | null>(null);

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
    // Step one writes the mandate's own role title, so the projects list's Role column goes stale.
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
    persist((panels: { technical: Competency[]; behavioural: Competency[] }) =>
      positionApi.putCompetencies(projectId, panels.technical, panels.behavioural),
    ),
  );

  const channels = [
    detailsSave,
    contextSave,
    reportingSave,
    compensationSave,
    criteriaSave,
    competenciesSave,
  ];
  const statuses = channels.map((channel) => channel.status);
  const saveStatus = statuses.includes("saving")
    ? "saving"
    : statuses.includes("saved")
      ? "saved"
      : "idle";

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
  /**
   * Replaces every step's draft with a brief the server has just rewritten.
   *
   * Each step holds its own local copy, so a write that changes all six — only applying a template
   * does — has to reseat all of them. Skip one and its next autosave would put the old draft back
   * over the new brief, a step at a time.
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
    setLockedCompetencies(new Set());
  };

  /**
   * Draft this brief as the picked role, and take its title while we are at it.
   *
   * Pending edits go first: a title still inside the autosave debounce would otherwise land after
   * the redraft and reinstate the step it replaced. The title is then written through the ordinary
   * details save rather than by the template — the server keeps the two apart deliberately, and the
   * person who picked the row is the one renaming the search.
   */
  const applyTemplate = useMutation({
    mutationFn: async (template: PositionTemplate) => {
      await Promise.all(channels.map((channel) => channel.flush()));
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

  const changeCriteria = (next: Criterion[]) => {
    setCriteria(next);
    criteriaSave.schedule(next);
  };
  /**
   * The one place both competency panels are ever written, so two panels can be updated in the same
   * handler without either write reading the other's stale, pre-update value from this closure — the
   * hazard a `setTechnical` and a `setBehavioural` fired from two separate calls run straight into.
   */
  const changeCompetencyPanels = (
    nextTechnical: IdentifiedCompetency[],
    nextBehavioural: IdentifiedCompetency[],
    immediate = false,
  ) => {
    setTechnical(nextTechnical);
    setBehavioural(nextBehavioural);
    competenciesSave.schedule({
      technical: forWire(nextTechnical),
      behavioural: forWire(nextBehavioural),
    });
    if (immediate) void competenciesSave.flush();
  };
  const changePanel =
    (panel: CompetencyPanelKey, immediate = false) =>
    (rows: IdentifiedCompetency[]) =>
      changeCompetencyPanels(
        panel === "technical" ? rows : technical,
        panel === "behavioural" ? rows : behavioural,
        immediate,
      );

  /** Where a published brief leads: the mandate's own market, which is the next thing to be done. */
  const goToStrategy = () => navigate(`/projects/${projectId}/strategy`);

  const selectStep = (key: StepKey) => {
    // Opening a step of a published brief is the same statement as "Edit position": the fields are
    // right there and live. Anything else would leave the rail claiming a read-back it is not doing.
    if (position.publication.publishedAt && key !== "review") setEditingPublished(true);
    setCurrentStep(key);
    setFurthestStep((furthest) =>
      stepIndexOf(key) > stepIndexOf(furthest) ? key : furthest,
    );
  };

  const publishNow = () =>
    position.publication.publishedAt ? void publishChanges() : publish.mutate();

  const editPosition = () => {
    setEditingPublished(true);
    selectStep("review");
  };

  /** Reordering is the ranking, and a decision rather than typing — so it saves at once. */
  const reorderPanel = (panel: CompetencyPanelKey) => (fromId: string, toId: string) => {
    const rows = panel === "technical" ? technical : behavioural;
    changePanel(panel, true)(moveRow(rows, fromId, toId));
  };

  const publish = useMutation({
    mutationFn: () => positionApi.publish(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      toast("Position profile published");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const withdraw = useMutation({
    mutationFn: () => positionApi.withdrawPublication(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      setEditingPublished(false);
      toast("Publication withdrawn");
    },
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * Publishing a brief that is already published. The stamp does not move — it records when the
   * brief was first called ready and a second click must not rewrite it — so what this does is flush
   * what the edits left in flight and close the review back up. It is the same act from where the
   * consultant sits: they said it was ready, and they are saying it again.
   */
  const publishChanges = async () => {
    await flushEverything();
    setEditingPublished(false);
    toast("Changes published");
  };

  const attachDocument = useMutation({
    mutationFn: (file: File) => positionApi.attachDocument(projectId, file),
    // A proposal against a document that has just been replaced is confusing, so it does not survive
    // on any step.
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      setDetailsExtraction(null);
      setContextExtraction(null);
      setCompensationExtraction(null);
      setAssessmentExtraction(null);
    },
    onError: (error) => toast(messageFor(error)),
  });
  const removeDocument = useMutation({
    mutationFn: () => positionApi.removeDocument(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      setDetailsExtraction(null);
      setContextExtraction(null);
      setCompensationExtraction(null);
      setAssessmentExtraction(null);
    },
    onError: (error) => toast(messageFor(error)),
  });
  const downloadDocument = useMutation({
    mutationFn: () =>
      positionApi.saveDocument(projectId, position.document?.fileName ?? "position-description"),
    onError: (error) => toast(messageFor(error)),
  });
  const extractDetails = useMutation({
    mutationFn: () => positionApi.extractDetails(projectId),
    onSuccess: setDetailsExtraction,
    onError: (error) => toast(messageFor(error)),
  });
  const extractContext = useMutation({
    mutationFn: () => positionApi.extractContext(projectId),
    onSuccess: setContextExtraction,
    onError: (error) => toast(messageFor(error)),
  });
  const extractCompensation = useMutation({
    mutationFn: () => positionApi.extractCompensation(projectId),
    onSuccess: setCompensationExtraction,
    onError: (error) => toast(messageFor(error)),
  });
  const extractAssessment = useMutation({
    mutationFn: () => positionApi.extractAssessment(projectId),
    onSuccess: setAssessmentExtraction,
    onError: (error) => toast(messageFor(error)),
  });

  /** Removed by object identity, never by index — a row's identity must not shift under a disclosure
   * left open while another row is accepted or dismissed beside it. */
  const removeDetailsProposal = (field: ProposedField) =>
    setDetailsExtraction((current) =>
      current ? { ...current, fields: current.fields.filter((row) => row !== field) } : current,
    );

  /** Null for a fieldKey this step doesn't have a slot for — the caller must not treat that as "saved". */
  const patchForDetails = (field: ProposedField, value: string): Partial<PositionDetails> | null => {
    switch (field.fieldKey) {
      case "roleTitle":
        return { roleTitle: value };
      case "department":
        return { department: value || null };
      case "location":
        return { location: value || null };
      case "employmentType":
        return isEmploymentType(value) ? { employmentType: value } : null;
      case "seniority":
        return isSeniority(value) ? { seniority: value } : null;
      case "narrative":
        return { narrative: value || null };
      case "responsibility":
        return { responsibilities: [...details.responsibilities, value] };
      default:
        return null;
    }
  };

  const acceptDetailsProposal = (field: ProposedField, value: string) => {
    const patch = patchForDetails(field, value);
    if (!patch) return;
    // Renaming the mandate is a decision, like every other immediate-flagged edit in this file — every
    // other field stays on the ordinary debounce a typed edit would get.
    changeDetails(patch, field.fieldKey === "roleTitle");
    removeDetailsProposal(field);
  };

  /**
   * One combined patch rather than one `changeDetails` call per field: `changeDetails` reads `details`
   * from this closure rather than a functional updater, so several calls fired synchronously in the
   * same handler would each start from the same stale snapshot and the later ones would silently
   * discard the earlier ones' edits.
   *
   * `edits` is the panel's own per-row corrections, keyed by field id — reading `field.value` alone
   * here would silently drop everything a user had typed before pressing Accept all.
   */
  const acceptAllDetailsProposals = (edits: Record<number, string>) => {
    if (!detailsExtraction) return;
    const valueOf = (field: ProposedField) => edits[field.id] ?? field.value;
    const responsibilities = detailsExtraction.fields
        .filter((field) => field.fieldKey === "responsibility")
        .map(valueOf);
    const combined = detailsExtraction.fields
        .filter((field) => field.fieldKey !== "responsibility")
        .reduce<Partial<PositionDetails>>((patch, field) => {
          const fieldPatch = patchForDetails(field, valueOf(field));
          return fieldPatch ? { ...patch, ...fieldPatch } : patch;
        }, {});
    changeDetails(
      { ...combined, responsibilities: [...details.responsibilities, ...responsibilities] },
      true,
    );
    setDetailsExtraction(null);
  };

  const removeContextProposal = (field: ProposedField) =>
    setContextExtraction((current) =>
      current ? { ...current, fields: current.fields.filter((row) => row !== field) } : current,
    );

  /** A name that case-insensitively matches one already on the brief is merged in place, never appended. */
  const mergePriorities = (existing: StrategicPriority[], names: string[]): StrategicPriority[] => {
    const merged = [...existing];
    for (const name of names) {
      const alreadyPresent = merged.some(
        (priority) => priority.name.toLowerCase() === name.toLowerCase(),
      );
      if (!alreadyPresent) merged.push({ name, selected: true });
    }
    return merged;
  };

  const patchForContext = (field: ProposedField, value: string): Partial<MandateContext> => {
    switch (field.fieldKey) {
      case "mandateReason":
        return { mandateReason: value as MandateContext["mandateReason"] };
      case "businessDriver":
        return { businessDriver: value || null };
      case "strategicPriority":
        return { strategicPriorities: mergePriorities(context.strategicPriorities, [value]) };
      default:
        return {};
    }
  };

  const acceptContextProposal = (field: ProposedField, value: string) => {
    changeContext(patchForContext(field, value));
    removeContextProposal(field);
  };

  const acceptAllContextProposals = () => {
    if (!contextExtraction) return;
    const priorityNames = contextExtraction.fields
        .filter((field) => field.fieldKey === "strategicPriority")
        .map((field) => field.value);
    const combined = contextExtraction.fields
        .filter((field) => field.fieldKey !== "strategicPriority")
        .reduce<Partial<MandateContext>>(
          (patch, field) => ({ ...patch, ...patchForContext(field, field.value) }),
          {},
        );
    changeContext(
      { ...combined, strategicPriorities: mergePriorities(context.strategicPriorities, priorityNames) },
      true,
    );
    setContextExtraction(null);
  };

  const removeCompensationProposal = (field: ProposedField) =>
    setCompensationExtraction((current) =>
      current ? { ...current, fields: current.fields.filter((row) => row !== field) } : current,
    );

  /**
   * A proposed benefit's `value` is `"<name>"`, or `"<name> — <frequency>"` when the document's own
   * wording gave the proposer a frequency it could resolve — see `PositionCompensationProposer`. The
   * amount is never proposed, so it always lands `null`, exactly like a manually added benefit row.
   *
   * Anchored to the end of the string and to the two literal tokens the backend ever appends, so a
   * benefit name that itself contains " — " in the middle is never mistaken for the appended suffix —
   * only an exact, backend-appended trailing " — monthly"/" — yearly" is split off.
   */
  const benefitFrom = (value: string): Benefit => {
    const suffix = value.match(new RegExp(`^(.*)${PACK_SEPARATOR}(monthly|yearly)$`, "i"));
    if (!suffix) {
      return { name: value, amount: null, frequency: "MONTHLY" };
    }
    const frequency: BenefitFrequency = suffix[2].toUpperCase() === "YEARLY" ? "YEARLY" : "MONTHLY";
    return { name: suffix[1], amount: null, frequency };
  };

  const patchForCompensation = (field: ProposedField, value: string): Partial<Compensation> => {
    switch (field.fieldKey) {
      case "currency":
        return { currency: value };
      case "salaryMin":
        return { salaryMin: Number(value) };
      case "salaryMax":
        return { salaryMax: Number(value) };
      case "baseSalaryMode":
        return { baseSalaryMode: value as Compensation["baseSalaryMode"] };
      case "bonusValue":
        return { bonusValue: Number(value) };
      case "bonusBasis":
        return { bonusBasis: value as Compensation["bonusBasis"] };
      case "incentiveType":
        return { incentiveType: value as Compensation["incentiveType"] };
      case "incentiveAmount":
        return { incentiveAmount: Number(value) };
      case "incentiveVesting":
        return { incentiveVesting: value || null };
      case "benefit":
        return { benefits: [...compensation.benefits, benefitFrom(value)] };
      default:
        return {};
    }
  };

  const acceptCompensationProposal = (field: ProposedField, value: string) => {
    changeCompensation(patchForCompensation(field, value));
    removeCompensationProposal(field);
  };

  const acceptAllCompensationProposals = () => {
    if (!compensationExtraction) return;
    const benefits = compensationExtraction.fields
        .filter((field) => field.fieldKey === "benefit")
        .map((field) => benefitFrom(field.value));
    const combined = compensationExtraction.fields
        .filter((field) => field.fieldKey !== "benefit")
        .reduce<Partial<Compensation>>(
          (patch, field) => ({ ...patch, ...patchForCompensation(field, field.value) }),
          {},
        );
    changeCompensation(
      { ...combined, benefits: [...compensation.benefits, ...benefits] },
      true,
    );
    setCompensationExtraction(null);
  };

  const removeAssessmentProposal = (field: ProposedField) =>
    setAssessmentExtraction((current) =>
      current ? { ...current, fields: current.fields.filter((row) => row !== field) } : current,
    );

  /**
   * Folds one proposed field into an assessment accumulator — the one place the fieldKey → state-slot
   * mapping lives, so `acceptAssessmentProposal` and `acceptAllAssessmentProposals` read it the same
   * way instead of each keeping their own copy. Returns `acc` unchanged, rather than over-filling it,
   * once a group is already at `PutCriteriaRequest`'s/`PutCompetenciesRequest`'s own per-brief ceiling
   * — those ceilings are per brief, not per proposal, so a brief already near one can still not take
   * everything an "Accept all" offers.
   *
   * A criterion built from an accepted proposal is written `fromBrief: false`, exactly like one typed
   * by hand into `CriteriaCard` — never `true`. `fromBrief` marks a row a template redraft is free to
   * delete and replace (`PositionTemplateApplier.draftedCriteria`); a criterion a person read out of
   * the client's own document and accepted is not the template's to discard on the next re-apply.
   */
  const patchForAssessment = (
    field: ProposedField,
    value: string,
    acc: AssessmentAccumulator,
  ): AssessmentAccumulator => {
    switch (field.fieldKey) {
      case "requiredCriterion":
        return acc.criteria.length >= CRITERIA_MAX_COUNT
          ? acc
          : { ...acc, criteria: [...acc.criteria, { text: value, mode: "REQUIRED", fromBrief: false }] };
      case "preferredCriterion":
        return acc.criteria.length >= CRITERIA_MAX_COUNT
          ? acc
          : { ...acc, criteria: [...acc.criteria, { text: value, mode: "PREFERRED", fromBrief: false }] };
      case "technicalCompetency":
        return acc.technical.length >= COMPETENCY_MAX_COUNT_PER_PANEL
          ? acc
          : { ...acc, technical: [...acc.technical, { ...competencyFrom(value), id: crypto.randomUUID() }] };
      case "behaviouralCompetency":
        return acc.behavioural.length >= COMPETENCY_MAX_COUNT_PER_PANEL
          ? acc
          : { ...acc, behavioural: [...acc.behavioural, { ...competencyFrom(value), id: crypto.randomUUID() }] };
      default:
        return acc;
    }
  };

  /** Writes whichever of `after`'s three slots actually changed from `before`, in one combined write
   *  per channel — `changeCriteria`/`changeCompetencyPanels` read their current arrays from this
   *  closure rather than a functional updater, so this must be the only call each makes. */
  const writeAssessmentAccumulator = (before: AssessmentAccumulator, after: AssessmentAccumulator) => {
    if (after.criteria !== before.criteria) changeCriteria(after.criteria);
    if (after.technical !== before.technical || after.behavioural !== before.behavioural) {
      changeCompetencyPanels(after.technical, after.behavioural, true);
    }
  };

  const acceptAssessmentProposal = (field: ProposedField, value: string) => {
    const before: AssessmentAccumulator = { criteria, technical, behavioural };
    const after = patchForAssessment(field, value, before);
    if (after === before) {
      toast("This brief is already at its limit for that — remove something first.");
      return;
    }
    writeAssessmentAccumulator(before, after);
    removeAssessmentProposal(field);
  };

  const dismissAssessmentProposal = (field: ProposedField) => removeAssessmentProposal(field);

  const acceptAllAssessmentProposals = (edits: Record<number, string>) => {
    if (!assessmentExtraction) return;
    const valueOf = (field: ProposedField) => edits[field.id] ?? field.value;
    const before: AssessmentAccumulator = { criteria, technical, behavioural };
    const after = assessmentExtraction.fields.reduce(
      (acc, field) => patchForAssessment(field, valueOf(field), acc),
      before,
    );
    writeAssessmentAccumulator(before, after);
    const added =
      (after.criteria.length - before.criteria.length) +
      (after.technical.length - before.technical.length) +
      (after.behavioural.length - before.behavioural.length);
    if (added < assessmentExtraction.fields.length) {
      toast(
        `${assessmentExtraction.fields.length - added} of ${assessmentExtraction.fields.length} ` +
          "proposals could not be added — the brief is already at its limit.",
      );
    }
    setAssessmentExtraction(null);
  };

  const flushEverything = () => Promise.allSettled(channels.map((channel) => channel.flush()));

  const saveDraft = async () => {
    await flushEverything();
    toast("Draft saved");
  };

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
    },
  };
  const step = POSITION_STEPS[stepIndexOf(currentStep)];

  return (
    <div className="animate-fade-up">
      <div className="mb-[18px] flex items-center justify-end gap-2">
        <button
          type="button"
          title="Click to toggle confidentiality"
          onClick={() => changeContext({ confidential: !context.confidential }, true)}
          className={cn(
            "rounded-md border px-[11px] py-[5px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.05em] transition",
            context.confidential
              ? "border-red bg-red-dim text-red"
              : "border-line bg-panel text-text3 hover:border-text3",
          )}
        >
          {context.confidential ? "Confidential" : "Standard"}
        </button>
        <span
          className={cn(
            "rounded-md border px-[11px] py-[5px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em]",
            drafted.publication.publishedAt
              ? "border-transparent bg-green-dim text-green"
              : "border-line bg-panel text-text2",
          )}
        >
          {drafted.publication.publishedAt ? "✓ Published" : "Draft"}
        </span>
        <span aria-live="polite" className="w-14 text-end font-mono text-[11px] text-text3">
          {saveStatus === "saving" ? "Saving…" : saveStatus === "saved" ? "Saved" : ""}
        </span>
      </div>

      <div className="flex flex-wrap items-start gap-[22px]">
        <div className="order-2 min-w-0 flex-[2_1_460px] md:order-1">
          <div className="mb-[22px] flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
            {/* flex-1 with a floor, so the blurb gives way to the field beside it rather than taking
                the whole row and pushing it onto the next one. */}
            <div className="min-w-[240px] flex-1">
              <h2 className="text-[19px] font-bold tracking-[-0.01em] text-text">{step.heading}</h2>
              <p className="mt-[5px] max-w-[62ch] text-[13px] text-text3">{step.subheading}</p>
            </div>
            {currentStep === "context" && (
              <MandateReasonField
                value={context.mandateReason}
                onChange={(mandateReason) => changeContext({ mandateReason }, true)}
              />
            )}
          </div>

          {currentStep === "details" && (
            <PositionDetailsStep
              details={details}
              document={drafted.document}
              templates={templates}
              applyingTemplate={applyTemplate.isPending}
              uploading={attachDocument.isPending || removeDocument.isPending}
              extraction={detailsExtraction}
              extracting={extractDetails.isPending}
              onDownload={() => downloadDocument.mutate()}
              onChange={changeDetails}
              onPickTemplate={(template) => applyTemplate.mutate(template)}
              onAttachDocument={(file) => attachDocument.mutate(file)}
              onRemoveDocument={() => removeDocument.mutate()}
              onExtract={() => extractDetails.mutate()}
              onAcceptProposal={acceptDetailsProposal}
              onDismissProposal={removeDetailsProposal}
              onAcceptAllProposals={acceptAllDetailsProposals}
            />
          )}
          {currentStep === "context" && (
            <MandateContextStep
              context={context}
              document={drafted.document}
              extraction={contextExtraction}
              extracting={extractContext.isPending}
              onChange={changeContext}
              onExtract={() => extractContext.mutate()}
              onAcceptProposal={acceptContextProposal}
              onDismissProposal={removeContextProposal}
              onAcceptAllProposals={acceptAllContextProposals}
            />
          )}
          {currentStep === "reporting" && (
            <ReportingStructureStep
              roleTitle={details.roleTitle}
              seniority={details.seniority}
              reporting={reporting}
              onChange={changeReporting}
            />
          )}
          {currentStep === "compensation" && (
            <CompensationStep
              compensation={compensation}
              document={drafted.document}
              extraction={compensationExtraction}
              extracting={extractCompensation.isPending}
              onChange={changeCompensation}
              onExtract={() => extractCompensation.mutate()}
              onAcceptProposal={acceptCompensationProposal}
              onDismissProposal={removeCompensationProposal}
              onAcceptAllProposals={acceptAllCompensationProposals}
            />
          )}
          {currentStep === "assessment" && (
            <AssessmentStep
              criteria={criteria}
              technical={technical}
              behavioural={behavioural}
              locked={lockedCompetencies}
              document={drafted.document}
              extraction={assessmentExtraction}
              extracting={extractAssessment.isPending}
              onCriteria={changeCriteria}
              onPanel={changePanel}
              onToggleLock={(id) => setLockedCompetencies((current) => toggle(current, id))}
              onReorder={reorderPanel}
              onExtract={() => extractAssessment.mutate()}
              onAcceptProposal={acceptAssessmentProposal}
              onDismissProposal={dismissAssessmentProposal}
              onAcceptAllProposals={acceptAllAssessmentProposals}
            />
          )}
          {currentStep === "review" && (
            <ReviewStep
              position={drafted}
              canEdit={!drafted.publication.publishedAt || editingPublished}
              onEditStep={selectStep}
              onWithdraw={editingPublished ? () => withdraw.mutate() : null}
            />
          )}

          <StepNavigation
            currentStep={currentStep}
            onSelectStep={selectStep}
            onPublish={publishNow}
            onGoToStrategy={goToStrategy}
            publishing={publish.isPending}
            published={Boolean(drafted.publication.publishedAt)}
          />
        </div>

        <StepRail
          position={drafted}
          currentStep={currentStep}
          furthestStep={furthestStep}
          onSelectStep={selectStep}
          onPublish={publishNow}
          onSaveDraft={() => void saveDraft()}
          onGoToStrategy={goToStrategy}
          onEditPosition={editPosition}
          editing={editingPublished}
          publishing={publish.isPending}
        />
      </div>
    </div>
  );
}
