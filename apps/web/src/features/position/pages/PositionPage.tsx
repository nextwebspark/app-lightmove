import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Competency, Criterion, Position, PositionDetails } from "../api/types";
import { CompetencyPanel } from "../components/CompetencyPanel";
import { CriteriaCard } from "../components/CriteriaCard";
import { IdealProfileCard } from "../components/IdealProfileCard";
import { LockFooter } from "../components/LockFooter";
import { MandateContextCard } from "../components/MandateContextCard";
import { PackageCard } from "../components/PackageCard";
import { PositionHero } from "../components/PositionHero";
import { ReportingStructureCard } from "../components/ReportingStructureCard";
import { SectionHeading } from "../components/fields";
import { useAutosave } from "../../../lib/useAutosave";
import { completion, readiness } from "../lib/readiness";

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

  // Remounting on lock-state changes resyncs the drafts with the server — including a lock made in
  // another tab, which arrives via the POSITION_LOCKED-triggered refetch.
  return <PositionEditor key={`${project.id}-${position.locked}`} project={project} position={position} />;
}

/**
 * The brief editor (Project.dc.html, Position page). There is no Save button: each section's draft
 * autosaves as a snapshot PUT — scalars, criteria and competencies independently — and the hero
 * shows the collective Saving…/Saved state. Locking freezes the whole brief; the fieldset disables
 * every input at once and Unlock is offered to admins.
 */
function PositionEditor({ project, position }: { project: Project; position: Position }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = positionApi.POSITION_KEY(project.id);

  const [details, setDetails] = useState<PositionDetails>(() => detailsOf(position));
  const [criteria, setCriteria] = useState<Criterion[]>(position.criteria);
  const [technical, setTechnical] = useState<Competency[]>(position.technical);
  const [behavioural, setBehavioural] = useState<Competency[]>(position.behavioural);

  /** Shared persistence shape: cache the returned snapshot; toast failures; resync if locked. */
  const persist =
    <T,>(call: (payload: T) => Promise<Position>, onSaved?: () => void) =>
    async (payload: T) => {
      try {
        queryClient.setQueryData(key, await call(payload));
        onSaved?.();
      } catch (error) {
        toast(messageFor(error));
        if (codeOf(error) === "POSITION_LOCKED") {
          void queryClient.invalidateQueries({ queryKey: key });
        }
        throw error;
      }
    };

  const detailsSave = useAutosave(
    // The scalar save writes the project's target date too, so refresh the list's Target column.
    persist(
      (d: PositionDetails) => positionApi.putPosition(project.id, d),
      () => void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY }),
    ),
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

  const lock = useMutation({
    mutationFn: async () => {
      // A half-typed edit must land before the gate is judged server-side.
      await Promise.all([detailsSave.flush(), criteriaSave.flush(), competenciesSave.flush()]);
      return positionApi.lockPosition(project.id);
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      toast("Position locked — this is now the benchmark for candidate fit");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const unlock = useMutation({
    mutationFn: () => positionApi.unlockPosition(project.id),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      toast("Position unlocked — edits are live again");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const locked = position.locked;
  const gate = readiness({ technical, behavioural, criteria });
  const statuses = [detailsSave.status, criteriaSave.status, competenciesSave.status];
  const saveStatus = statuses.includes("saving")
    ? "saving"
    : statuses.includes("saved")
      ? "saved"
      : "idle";

  const seat = project.team.find((member) => member.userId === user?.id);
  const canUnlock =
    (user?.workspace?.roles.includes("ADMIN") ?? false) ||
    (seat?.projectRoles.includes("ADMIN") ?? false);

  return (
    <div className="animate-fade-up">
      <PositionHero
        project={project}
        details={details}
        locked={locked}
        completionPct={completion({ ...position, ...details, criteria, technical, behavioural })}
        saveStatus={saveStatus}
        onToggleConfidential={() => changeDetails({ confidential: !details.confidential }, true)}
      />

      {/* One switch freezes every control in the brief — the unlock button lives outside it. */}
      <fieldset disabled={locked} className="min-w-0">
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

        <PackageCard details={details} disabled={locked} onChange={(patch) => changeDetails(patch)} />

        <CriteriaCard criteria={criteria} disabled={locked} onChange={changeCriteria} />

        <div className="mb-[22px]">
          <SectionHeading
            title="Competency Weighting"
            aside="drag to rebalance · type a number to set exactly"
          />
          <div className="grid grid-cols-2 gap-4">
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
      </fieldset>

      <LockFooter
        locked={locked}
        readiness={gate}
        canUnlock={canUnlock}
        locking={lock.isPending}
        onLock={() => lock.mutate()}
        onUnlock={() => unlock.mutate()}
      />
    </div>
  );
}

function detailsOf(position: Position): PositionDetails {
  const { criteria: _c, technical: _t, behavioural: _b, locked: _l, lockedAt: _a, ...details } = position;
  return details;
}
