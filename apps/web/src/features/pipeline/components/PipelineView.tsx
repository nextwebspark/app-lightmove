import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { drawerSectionOf, POSITION_STAFF_TABS, type DrawerSection } from "../../candidates/lib/drawerTabs";
import { EmailComposerDialog } from "../../outreach/components/EmailComposerDialog";
import { NoteComposerModal } from "../../outreach/components/NoteComposerModal";
import { mailboxStateOf, type MailboxState } from "../../outreach/lib/mailboxState";
import { useMailbox } from "../../outreach/lib/useMailbox";
import type { Project } from "../../projects/api/types";
import { isArchived } from "../../projects/lib/filtering";
import { NextActionModal, type NextActionTarget } from "../../tasks/components/NextActionModal";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineRow } from "../api/types";
import { useBoardMove, useClientVisibility } from "../lib/usePipelineWrites";
import { PipelineBoard } from "./PipelineBoard";
import type { CardActionKind, CardActionsUnavailable } from "./PipelineCardActions";
import { ScheduleCallLauncher } from "./ScheduleCallLauncher";

/**
 * In universe read as the position's pipeline (`Position.dc.html`, `view=pipeline`): every executive on the position
 * as a board, narrowed by the toolbar's search, which the server matches as it does the grid's. `?candidate=` opens that executive's drawer —
 * where a notification, an email and My tasks send someone. A card's Log call, Email, Add note and Schedule open
 * their dialogs here, one at a time. Staff-only: the read is the position's WORK_EXECUTE.
 */
export function PipelineView({
  project,
  search,
  selectedIds,
  onToggleSelected,
}: {
  project: Project;
  /** Already debounced. */
  search: string;
  selectedIds: ReadonlySet<string>;
  onToggleSelected: (candidateId: string) => void;
}) {
  const queryClient = useQueryClient();
  const { catalog } = usePipelineCatalog();
  const [searchParams, setSearchParams] = useSearchParams();
  const [nextActionFor, setNextActionFor] = useState<NextActionTarget | null>(null);
  const [cardAction, setCardAction] = useState<{ kind: CardActionKind; row: PipelineRow } | null>(null);
  const mailbox = useMailbox();

  const openCandidateId = searchParams.get("candidate");
  const focusNoteId = searchParams.get("note");
  const focusSection = drawerSectionOf(searchParams.get("section"));
  const requestedTab = searchParams.get("tab");
  const focusTab = POSITION_STAFF_TABS.find((tab) => tab === requestedTab) ?? null;
  const setOpenCandidateId = (candidateId: string | null, section: DrawerSection | null = null) =>
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (candidateId) next.set("candidate", candidateId);
      else next.delete("candidate");
      next.delete("note");
      next.delete("tab");
      if (section) next.set("section", section);
      else next.delete("section");
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

  const actionsUnavailable = cardActionsUnavailableOf(mailboxStateOf(mailbox), isArchived(project));
  const refreshPipeline = () =>
    void queryClient.invalidateQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(project.id) });
  const closeCardAction = () => {
    setCardAction(null);
    refreshPipeline();
  };
  const openContact = (row: PipelineRow) => setOpenCandidateId(row.candidateId, "contact");

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
            actionsUnavailable={actionsUnavailable}
            onOpen={(candidateId) => setOpenCandidateId(candidateId)}
            onMove={(placement) => boardMove.mutate(placement)}
            onToggleVisible={(row) => visibility.mutate({ candidateId: row.candidateId, clientVisible: !row.clientVisible })}
            onOpenNextAction={openNextAction}
            onAction={(kind, row) => setCardAction({ kind, row })}
          />
        </>
      )}

      <NextActionModal target={nextActionFor} onClose={() => setNextActionFor(null)} />

      <NoteComposerModal
        projectId={project.id}
        candidateId={cardAction?.row.candidateId ?? ""}
        fullName={cardAction?.row.fullName ?? ""}
        kind={cardAction?.kind === "call" ? "call" : cardAction?.kind === "note" ? "general" : null}
        onClose={() => setCardAction(null)}
        onSaved={refreshPipeline}
      />
      {cardAction?.kind === "email" && (
        <EmailComposerDialog
          projectId={project.id}
          candidateId={cardAction.row.candidateId}
          fullName={cardAction.row.fullName}
          onClose={closeCardAction}
        />
      )}
      {cardAction?.kind === "schedule" && (
        <ScheduleCallLauncher
          projectId={project.id}
          row={cardAction.row}
          onClose={closeCardAction}
          onOpenContact={openContact}
        />
      )}

      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        focusNoteId={focusNoteId}
        focusSection={focusSection}
        focusTab={focusTab}
        onClose={() => setOpenCandidateId(null)}
        onChanged={refreshPipeline}
      />
    </>
  );
}

/** Why a card's Email or Schedule cannot be pressed now, in the words the composer itself would use. */
function cardActionsUnavailableOf(mailbox: MailboxState, archived: boolean): CardActionsUnavailable {
  if (archived) return { email: "This position is archived", schedule: "This position is archived" };
  if (mailbox.kind === "unavailable") {
    const off = "Outreach email isn't switched on for your workspace";
    return { email: off, schedule: off };
  }
  const schedule =
    mailbox.kind === "loading"
      ? "Checking your mailbox…"
      : mailbox.kind === "refused"
        ? "Your mailbox couldn't be read just now"
        : mailbox.kind === "none"
          ? "Connect your mailbox to book a call"
          : mailbox.kind === "error"
            ? "Your mailbox needs reconnecting before a call can be booked"
            : null;
  return { email: null, schedule };
}
