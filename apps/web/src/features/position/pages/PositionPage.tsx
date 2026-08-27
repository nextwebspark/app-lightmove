import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../../reports/api/reportApi";
import * as positionApi from "../api/positionApi";
import type { Competency, Criterion, Position, PositionDetails } from "../api/types";
import { CompetencyPanel } from "../components/CompetencyPanel";
import { CriteriaCard } from "../components/CriteriaCard";
import { IdealProfileCard } from "../components/IdealProfileCard";
import { MandateContextCard } from "../components/MandateContextCard";
import { PackageCard } from "../components/PackageCard";
import { PositionHero } from "../components/PositionHero";
import { ReportingStructureCard } from "../components/ReportingStructureCard";
import { SectionHeading } from "../components/fields";
import { useAutosave } from "../../../lib/useAutosave";
import { completion } from "../lib/completion";

/** The Position tab: loads the brief, then hands the editor a snapshot to draft against. */
export function PositionPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: position } = useQuery({
    queryKey: positionApi.POSITION_KEY(project.id),
    queryFn: () => positionApi.getPosition(project.id),
  });

  if (!position) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  return <PositionEditor key={project.id} project={project} position={position} />;
}

/**
 * The brief editor (Project.dc.html, Position page). There is no Save button: each section's draft
 * autosaves as a snapshot PUT — scalars, criteria and competencies independently — and the hero
 * shows the collective Saving…/Saved state.
 */
function PositionEditor({ project, position }: { project: Project; position: Position }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = positionApi.POSITION_KEY(project.id);

  const [details, setDetails] = useState<PositionDetails>(() => detailsOf(position));
  const [criteria, setCriteria] = useState<Criterion[]>(position.criteria);
  const [technical, setTechnical] = useState<Competency[]>(position.technical);
  const [behavioural, setBehavioural] = useState<Competency[]>(position.behavioural);

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
    // The scalar save writes the project's target date too, so refresh the list's Target column — and
    // the salary band, which the report restates as the mandate's band.
    persist((d: PositionDetails) => positionApi.putPosition(project.id, d), () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      void queryClient.invalidateQueries({ queryKey: reportApi.REPORT_KEY(project.id) });
    }),
  );
  const criteriaSave = useAutosave(persist((c: Criterion[]) => positionApi.putCriteria(project.id, c)));
  const competenciesSave = useAutosave(
    persist((panels: { technical: Competency[]; behavioural: Competency[] }) =>
      positionApi.putCompetencies(project.id, panels.technical, panels.behavioural),
    ),
  );

  const changeDetails = (patch: Partial<PositionDetails>, immediate = false) => {
    const next = { ...details, ...patch };
    setDetails(next);
    detailsSave.schedule(next);
    if (immediate) void detailsSave.flush();
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

  const statuses = [detailsSave.status, criteriaSave.status, competenciesSave.status];
  const saveStatus = statuses.includes("saving")
    ? "saving"
    : statuses.includes("saved")
      ? "saved"
      : "idle";

  return (
    <div className="animate-fade-up">
      <PositionHero
        project={project}
        details={details}
        completionPct={completion({ ...position, ...details, criteria, technical, behavioural })}
        saveStatus={saveStatus}
        onToggleConfidential={() => changeDetails({ confidential: !details.confidential }, true)}
      />

      <MandateContextCard
        reason={details.mandateReason}
        internalContext={details.internalContext}
        onReason={(mandateReason) => changeDetails({ mandateReason })}
        onContext={(internalContext) => changeDetails({ internalContext: internalContext || null })}
      />

      <IdealProfileCard
        narrative={details.narrative}
        onChange={(narrative) => changeDetails({ narrative: narrative || null })}
      />

      <ReportingStructureCard
        positionTitle={project.positionTitle}
        details={details}
        onChange={(patch) => changeDetails(patch)}
      />

      <PackageCard details={details} onChange={(patch) => changeDetails(patch)} />

      <CriteriaCard criteria={criteria} onChange={changeCriteria} />

      <div className="mb-[22px]">
        <SectionHeading
          title="Competency Weighting"
          aside="drag to rebalance · type a number to set exactly"
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <CompetencyPanel
            title="Technical Competencies"
            accent="sky"
            rows={technical}
            onChange={changePanel("technical")}
          />
          <CompetencyPanel
            title="Behavioural Competencies"
            accent="amber"
            rows={behavioural}
            onChange={changePanel("behavioural")}
          />
        </div>
      </div>
    </div>
  );
}

function detailsOf(position: Position): PositionDetails {
  const { criteria: _c, technical: _t, behavioural: _b, ...details } = position;
  return details;
}
