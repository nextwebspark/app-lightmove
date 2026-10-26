import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Navigate, useOutletContext, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { LinesSkeleton } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { ToolbarButton } from "../../../components/ui/ToolbarButton";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { EnrolDialog } from "../../outreach/components/EnrolDialog";
import { useMailbox } from "../../outreach/lib/useMailbox";
import { canExecuteProjectWork } from "../../projects/lib/access";
import { isArchived } from "../../projects/lib/filtering";
import { hasRoomForRails } from "../../../lib/viewport";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineRow } from "../api/types";
import { PipelineBoard } from "../components/PipelineBoard";
import { PipelineList } from "../components/PipelineList";
import { StagePills } from "../components/StagePills";
import { NextActionModal, type NextActionTarget } from "../../tasks/components/NextActionModal";
import { useBoardMove, useClientVisibility, useRankMove } from "../lib/usePipelineWrites";
import { drawerSectionOf } from "../../candidates/lib/drawerTabs";

type PipelineView = "list" | "board";

const VIEW_OPTIONS = [
  { value: "list" as const, label: "List", icon: <Icon d={ICONS.listView} size={14} /> },
  { value: "board" as const, label: "Board", icon: <Icon d={ICONS.boardView} size={14} /> },
];

const SEARCH_DEBOUNCE_MS = 250;

/**
 * A position's Pipeline (`Position.dc.html`, `pipeline`): every executive it maps, by stage, as a board
 * — how it opens — or a list. `?candidate=` opens that executive's drawer, which is where a notification, an
 * email and My tasks send someone. Staff-only — it is the position's WORK_EXECUTE, so a client seat is sent to In universe.
 */
export default function PipelinePage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  if (!canExecuteProjectWork(project, user?.id, user?.workspace?.roles)) {
    return <Navigate to={`/projects/${project.id}/companies/universe`} replace />;
  }
  return <StaffPipeline />;
}

function StaffPipeline() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const queryClient = useQueryClient();
  const { catalog, isLoaded: catalogLoaded } = usePipelineCatalog();
  const mailbox = useMailbox();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set());
  const [isEnrolling, setIsEnrolling] = useState(false);
  const [nextActionFor, setNextActionFor] = useState<NextActionTarget | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  const openCandidateId = searchParams.get("candidate");
  const focusNoteId = searchParams.get("note");
  const focusSection = drawerSectionOf(searchParams.get("section"));
  const setOpenCandidateId = (candidateId: string | null) =>
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (candidateId) next.set("candidate", candidateId);
      else next.delete("candidate");
      next.delete("note");
      next.delete("section");
      return next;
    });

  const requestedStage = searchParams.get("stage") ?? "";
  const stage =
    requestedStage && catalogLoaded && !catalog.stages.some((known) => known.key === requestedStage) ? "" : requestedStage;
  const setStage = (key: string) =>
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("view", "list");
      if (key) next.set("stage", key);
      else next.delete("stage");
      return next;
    });

  const requestedView = searchParams.get("view");
  // A board of columns is a desk's reading; a phone opens on the list unless a link asks for the board.
  const [boardFits] = useState(hasRoomForRails);
  const view: PipelineView =
    requestedView === "list" || requestedView === "board"
      ? requestedView
      : requestedStage || !boardFits
        ? "list"
        : "board";
  const setView = (next: PipelineView) =>
    setSearchParams((current) => {
      const params = new URLSearchParams(current);
      if (next === "board" && !params.has("stage") && boardFits) params.delete("view");
      else params.set("view", next);
      return params;
    });

  const query = { q, pipelineStage: view === "list" ? stage : "" };
  const queryKey = pipelineApi.PIPELINE_KEY(project.id, query);
  const pipeline = useQuery({
    queryKey,
    queryFn: ({ signal }) => pipelineApi.getPipeline(project.id, query, signal),
    placeholderData: keepPreviousData,
  });

  const visibility = useClientVisibility(project.id);
  const rankMove = useRankMove(project.id, queryKey);
  const boardMove = useBoardMove(project.id, queryKey);

  const rows = pipeline.data?.rows ?? [];
  const stageCounts = pipeline.data?.stageCounts ?? [];
  const outreachOffered = mailbox.data?.offered === true && !isArchived(project);

  const openNextAction = (row: PipelineRow) =>
    setNextActionFor({
      projectId: project.id,
      candidateId: row.candidateId,
      personId: row.personId,
      executiveName: row.fullName,
      taskId: row.nextAction?.taskId ?? null,
    });

  const toggleVisible = (row: PipelineRow) =>
    visibility.mutate({ candidateId: row.candidateId, clientVisible: !row.clientVisible });

  const toggleSelected = (candidateId: string) =>
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(candidateId)) next.delete(candidateId);
      else next.add(candidateId);
      return next;
    });

  const toggleAll = (candidateIds: string[]) =>
    setSelectedIds((current) => {
      const allOn = candidateIds.every((id) => current.has(id));
      const next = new Set(current);
      candidateIds.forEach((id) => (allOn ? next.delete(id) : next.add(id)));
      return next;
    });

  return (
    <div className="flex h-full min-h-0 w-full flex-col">
      <div className="flex flex-none flex-wrap items-center gap-3 border-b border-u-border px-5 py-2">
        <label className="flex min-w-[220px] max-w-[420px] flex-1 items-center gap-2 rounded-u-control border border-u-border-strong bg-u-surface px-3 py-1.5">
          <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search candidates, keywords, notes..."
            aria-label="Search candidates"
            className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
          />
        </label>
        <div className="ms-auto flex items-center gap-2">
          <SegmentedControl label="Pipeline view" options={VIEW_OPTIONS} value={view} onChange={setView} />
          {outreachOffered && (
            <ToolbarButton
              disabled={selectedIds.size === 0}
              title="Add the ticked executives to an outreach sequence"
              onClick={() => setIsEnrolling(true)}
            >
              <Icon d={ICONS.outreach} size={14} />
              Add to sequence
            </ToolbarButton>
          )}
        </div>
      </div>

      {view === "list" && stageCounts.length > 0 && (
        <StagePills stageCounts={stageCounts} selected={stage} onSelect={setStage} />
      )}

      {pipeline.isError ? (
        <p role="alert" className="px-5 py-6 text-note text-u-text3">
          {messageFor(pipeline.error)}
        </p>
      ) : pipeline.isPending ? (
        <div className="px-5 py-4">
          <LinesSkeleton lines={6} />
        </div>
      ) : rows.length === 0 && view === "list" ? (
        <p className="px-5 py-6 text-note text-u-text3">
          {q || stage
            ? "No executives match."
            : "No executives on this position yet. Map them from In universe and they appear here."}
        </p>
      ) : view === "list" ? (
        <PipelineList
          projectId={project.id}
          rows={rows}
          selectedIds={selectedIds}
          onToggleSelected={toggleSelected}
          onToggleAll={toggleAll}
          onOpen={setOpenCandidateId}
          onMove={(moved, candidateId) => rankMove.mutate({ candidateId, move: moved.move, rows: moved.rows })}
          onToggleVisible={toggleVisible}
          onOpenNextAction={openNextAction}
        />
      ) : (
        <PipelineBoard
          projectId={project.id}
          rows={rows}
          stageCounts={stageCounts}
          catalog={catalog}
          selectedIds={selectedIds}
          onToggleSelected={toggleSelected}
          onOpen={setOpenCandidateId}
          onMove={(placement) => boardMove.mutate(placement)}
          onToggleVisible={toggleVisible}
          onOpenNextAction={openNextAction}
        />
      )}

      <NextActionModal target={nextActionFor} onClose={() => setNextActionFor(null)} />

      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        focusNoteId={focusNoteId}
        focusSection={focusSection}
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(project.id) })}
      />

      {isEnrolling && (
        <EnrolDialog
          projectId={project.id}
          scope={{ candidateIds: [...selectedIds] }}
          source={`From the ${selectedIds.size === 1 ? "executive" : `${selectedIds.size} executives`} you ticked on Pipeline`}
          onClose={() => setIsEnrolling(false)}
        />
      )}
    </div>
  );
}
