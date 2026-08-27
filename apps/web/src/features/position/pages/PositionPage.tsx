import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useAutosave } from "../../../lib/useAutosave";
import * as projectsApi from "../../projects/api/projectsApi";
import * as reportApi from "../../reports/api/reportApi";
import * as positionApi from "../api/positionApi";
import type {
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  Position,
  PositionDetails,
  ReportingStructure,
} from "../api/types";
import { StepNavigation } from "../components/StepNavigation";
import { StepRail } from "../components/StepRail";
import { AssessmentStep } from "../components/steps/AssessmentStep";
import { CompensationStep } from "../components/steps/CompensationStep";
import { MandateContextStep } from "../components/steps/MandateContextStep";
import { PositionDetailsStep } from "../components/steps/PositionDetailsStep";
import { ReportingStructureStep } from "../components/steps/ReportingStructureStep";
import { ReviewStep } from "../components/steps/ReviewStep";
import { POSITION_STEPS, stepIndexOf, type StepKey } from "../lib/steps";

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
  const toast = useToast();
  const key = positionApi.POSITION_KEY(projectId);

  const [currentStep, setCurrentStep] = useState<StepKey>("details");
  const [details, setDetails] = useState<PositionDetails>(position.details);
  const [context, setContext] = useState<MandateContext>(position.context);
  const [reporting, setReporting] = useState<ReportingStructure>(position.reporting);
  const [compensation, setCompensation] = useState<Compensation>(position.compensation);
  const [criteria, setCriteria] = useState<Criterion[]>(position.assessment.criteria);
  const [technical, setTechnical] = useState<Competency[]>(position.assessment.technical);
  const [behavioural, setBehavioural] = useState<Competency[]>(position.assessment.behavioural);

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
    // Step three writes the mandate's one target date, which the list's Target column shows.
    persist((next: ReportingStructure) => positionApi.putReporting(projectId, next), () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
    }),
  );
  const compensationSave = useAutosave(
    // The report restates this band as the mandate's, so it goes stale with every package edit.
    persist((next: Compensation) => positionApi.putCompensation(projectId, next), () => {
      void queryClient.invalidateQueries({ queryKey: reportApi.REPORT_KEY(projectId) });
    }),
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
  const changeCriteria = (next: Criterion[]) => {
    setCriteria(next);
    criteriaSave.schedule(next);
  };
  const changePanel = (panel: "technical" | "behavioural") => (rows: Competency[]) => {
    const next = {
      technical: panel === "technical" ? rows : technical,
      behavioural: panel === "behavioural" ? rows : behavioural,
    };
    setTechnical(next.technical);
    setBehavioural(next.behavioural);
    competenciesSave.schedule(next);
  };

  const publish = useMutation({
    mutationFn: () =>
      position.publication.publishedAt
        ? positionApi.withdrawPublication(projectId)
        : positionApi.publish(projectId),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      toast(saved.publication.publishedAt ? "Position profile published" : "Publication withdrawn");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const attachDocument = useMutation({
    mutationFn: (file: File) => positionApi.attachDocument(projectId, file),
    onSuccess: (saved) => queryClient.setQueryData(key, saved),
    onError: (error) => toast(messageFor(error)),
  });
  const removeDocument = useMutation({
    mutationFn: () => positionApi.removeDocument(projectId),
    onSuccess: (saved) => queryClient.setQueryData(key, saved),
    onError: (error) => toast(messageFor(error)),
  });

  const saveDraft = async () => {
    await Promise.allSettled(channels.map((channel) => channel.flush()));
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
    assessment: { criteria, technical, behavioural },
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
          <div className="mb-[22px]">
            <h2 className="text-[19px] font-bold tracking-[-0.01em] text-text">{step.heading}</h2>
            <p className="mt-[5px] max-w-[62ch] text-[13px] text-text3">{step.subheading}</p>
          </div>

          {currentStep === "details" && (
            <PositionDetailsStep
              details={details}
              document={drafted.document}
              uploading={attachDocument.isPending || removeDocument.isPending}
              downloadUrl={positionApi.documentUrl(projectId)}
              onChange={changeDetails}
              onAttachDocument={(file) => attachDocument.mutate(file)}
              onRemoveDocument={() => removeDocument.mutate()}
            />
          )}
          {currentStep === "context" && (
            <MandateContextStep context={context} onChange={changeContext} />
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
            <CompensationStep compensation={compensation} onChange={changeCompensation} />
          )}
          {currentStep === "assessment" && (
            <AssessmentStep
              assessment={drafted.assessment}
              onCriteria={changeCriteria}
              onPanel={changePanel}
            />
          )}
          {currentStep === "review" && (
            <ReviewStep position={drafted} onEditStep={setCurrentStep} />
          )}

          <StepNavigation
            currentStep={currentStep}
            onSelectStep={setCurrentStep}
            onPublish={() => publish.mutate()}
            publishing={publish.isPending}
            published={Boolean(drafted.publication.publishedAt)}
          />
        </div>

        <StepRail
          position={drafted}
          currentStep={currentStep}
          onSelectStep={setCurrentStep}
          onPublish={() => publish.mutate()}
          onSaveDraft={() => void saveDraft()}
          publishing={publish.isPending}
        />
      </div>
    </div>
  );
}
