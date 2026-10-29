import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { drawerSectionOf, POSITION_STAFF_TABS } from "../../candidates/lib/drawerTabs";
import type { Project } from "../../projects/api/types";
import { NextActionModal, type NextActionTarget } from "../../tasks/components/NextActionModal";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineRow } from "../api/types";
import { useBoardMove, useClientVisibility } from "../lib/usePipelineWrites";
import { PipelineBoard } from "./PipelineBoard";

/**
 * In universe read as the position's pipeline (`Position.dc.html`, `view=pipeline`): every executive on the position
 * as a board, narrowed by the toolbar's search, which the server matches as it does the grid's. `?candidate=` opens that executive's drawer —
 * where a notification, an email and My tasks send someone. Staff-only: the read is the position's WORK_EXECUTE.
 */
export function PipelineView({
  project,
  search,
  selectedIds,
  onToggleSelected,
  inactiveOpen,
  onInactiveOpenChange,
}: {
  project: Project;
  /** Already debounced. */
  search: string;
  selectedIds: ReadonlySet<string>;
  onToggleSelected: (candidateId: string) => void;
  inactiveOpen: boolean;
  onInactiveOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const { catalog } = usePipelineCatalog();
  const [searchParams, setSearchParams] = useSearchParams();
  const [nextActionFor, setNextActionFor] = useState<NextActionTarget | null>(null);

  const openCandidateId = searchParams.get("candidate");
  const focusNoteId = searchParams.get("note");
  const focusSection = drawerSectionOf(searchParams.get("section"));
  const requestedTab = searchParams.get("tab");
  const focusTab = POSITION_STAFF_TABS.find((tab) => tab === requestedTab) ?? null;
  const setOpenCandidateId = (candidateId: string | null) =>
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (candidateId) next.set("candidate", candidateId);
      else next.delete("candidate");
      next.delete("note");
      next.delete("section");
      next.delete("tab");
      return next;
    });

  const query = { q: search, pipelineStage: "" };
  const queryKey = pipelineApi.PIPELINE_KEY(project.id, query);
  const pipeline = useQuery({
    queryKey,
    queryFn: ({ signal }) => pipelineApi.getPipeline(project.id, query, signal),
    placeholderData: keepPreviousData,
  });

  const visibility = useClientVisibility(project.id);
  const boardMove = useBoardMove(project.id, queryKey);

  const rows = pipeline.data?.rows ?? [];
  const stageCounts = pipeline.data?.stageCounts ?? [];

  const openNextAction = (row: PipelineRow) =>
    setNextActionFor({
      projectId: project.id,
      candidateId: row.candidateId,
      personId: row.personId,
      executiveName: row.fullName,
      taskId: row.nextAction?.taskId ?? null,
    });

  return (
    <>
      {pipeline.isError ? (
        <p role="alert" className="px-5 py-6 text-note text-u-text3">
          {messageFor(pipeline.error)}
        </p>
      ) : pipeline.isPending ? (
        <div className="px-5 py-4">
          <LinesSkeleton lines={6} />
        </div>
      ) : (
        <>
          {rows.length === 0 && (
            <p role="status" className="px-5 pt-3 text-note text-u-text3">
              {search.trim()
                ? "No executives match that search."
                : "No executives on this position yet. Add one, or find executives from Table."}
            </p>
          )}
          <PipelineBoard
            projectId={project.id}
            rows={rows}
            stageCounts={stageCounts}
            catalog={catalog}
            selectedIds={selectedIds}
            onToggleSelected={onToggleSelected}
            onOpen={setOpenCandidateId}
            onMove={(placement) => boardMove.mutate(placement)}
            onToggleVisible={(row) => visibility.mutate({ candidateId: row.candidateId, clientVisible: !row.clientVisible })}
            onOpenNextAction={openNextAction}
            inactiveOpen={inactiveOpen}
            onInactiveOpenChange={onInactiveOpenChange}
          />
        </>
      )}

      <NextActionModal target={nextActionFor} onClose={() => setNextActionFor(null)} />

      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        focusNoteId={focusNoteId}
        focusSection={focusSection}
        focusTab={focusTab}
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(project.id) })}
      />
    </>
  );
}
