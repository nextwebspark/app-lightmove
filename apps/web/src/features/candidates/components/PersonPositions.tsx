import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import { changeCandidateStatus, CANDIDATES_KEY_PREFIX } from "../api/candidatesApi";
import * as poolApi from "../api/poolApi";
import type { CandidateStatus, PersonPosition } from "../api/types";
import { CANDIDATE_SOURCE_STYLES } from "../lib/candidateVocabulary";
import { useStatusStyle } from "../../workspace/lib/usePipelineCatalog";
import { StatusPicker } from "./StatusPicker";
import { TabSectionHeading } from "./PersonSections";
import { NO_POSITIONS_ORIGIN } from "../../projects/lib/positionsOrigin";
import { Popover } from "../../../components/ui/Popover";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { NextActionsPanel, useCandidateTasks, useTaskCompletion } from "../../tasks/components/NextActionBar";
import { DueText } from "../../tasks/components/TaskBits";
import { taskKindOf } from "../../tasks/lib/taskKinds";

/** Opens the Next action modal on one position's mapping: a task to edit, or a new one for null. */
export type SetNextActionOn = (position: PersonPosition, taskId: string | null) => void;

/**
 * The positions a person sits on, in either drawer's Positions tab. With `onSetNextAction`, each card
 * the viewer may work also carries that position's next action — a task belongs to one position, so
 * off a position this is where it lives.
 */
export function PositionsSection({
  title,
  positions,
  action,
  empty,
  onStatusChanged,
  onSetNextAction,
}: {
  title: string;
  positions: readonly PersonPosition[];
  action?: ReactNode;
  empty?: ReactNode;
  onStatusChanged?: () => void;
  onSetNextAction?: SetNextActionOn;
}) {
  return (
    <section aria-label={title} className="py-4">
      <TabSectionHeading title={title} action={action} />
      {positions.length === 0 ? (
        empty
      ) : (
        <ul className="flex flex-col gap-2">
          {positions.map((position) => (
            <PositionCard
              key={position.candidateId}
              position={position}
              onStatusChanged={onStatusChanged}
              onSetNextAction={onSetNextAction}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function PositionCard({
  position,
  onStatusChanged,
  onSetNextAction,
}: {
  position: PersonPosition;
  onStatusChanged?: () => void;
  onSetNextAction?: SetNextActionOn;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const styleOf = useStatusStyle();
  const status = styleOf(position.status, position);
  const moving = useMutation({
    mutationFn: (next: CandidateStatus) => changeCandidateStatus(position.projectId, position.candidateId, next),
    onSuccess: (_, next) => {
      toast(`Marked ${styleOf(next).label} on ${position.positionTitle ?? "the position"}`);
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(position.projectId) });
      onStatusChanged?.();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <li className="rounded-u-card border border-u-border">
      <div className="flex items-center gap-3 px-3 py-2.5">
      <Icon d={ICONS.position} size={15} className="flex-none text-u-text3" />
      <div className="min-w-0 flex-1">
        <Link
          to={`/projects/${position.projectId}/companies`}
          state={NO_POSITIONS_ORIGIN}
          className="block truncate text-[13px] font-semibold text-u-text hover:underline"
        >
          {position.positionTitle ?? "Untitled position"}
        </Link>
        <span className="block truncate font-mono text-[11px] text-u-text3">
          Added by {position.addedByName ?? "someone"} · {formatInstantDate(position.addedAt)} ·{" "}
          {CANDIDATE_SOURCE_STYLES[position.source]?.label ?? position.source}
        </span>
      </div>
      {position.workable ? (
        <StatusPicker
          aria-label={`Status on ${position.positionTitle ?? "this position"}`}
          value={position.status}
          currentLabel={position.statusLabel}
          disabled={moving.isPending}
          onChange={(status) => moving.mutate(status)}
          className="flex-none"
        />
      ) : (
        <span className={`flex-none rounded-full px-2 py-px font-mono text-[10px] font-semibold ${status.className}`}>
          {status.label}
        </span>
      )}
      </div>
      {onSetNextAction && position.workable && (
        <PositionNextAction position={position} onSetNextAction={onSetNextAction} />
      )}
    </li>
  );
}

/** A card's foot: the position's earliest open task, the rest a popover away, or the prompt to set one. */
function PositionNextAction({
  position,
  onSetNextAction,
}: {
  position: PersonPosition;
  onSetNextAction: SetNextActionOn;
}) {
  const tasks = useCandidateTasks(position.projectId, position.candidateId);
  const completion = useTaskCompletion(position.projectId);
  const open = tasks.data?.open ?? [];
  const first = open[0];
  const foot = "flex h-9 items-center gap-2 border-t border-u-border px-3 text-note";

  if (tasks.isPending || tasks.isError) return null;

  if (!first) {
    return (
      <div className={cn(foot, "text-u-text3")}>
        <Icon d={ICONS.calendar} size={13} className="flex-none" />
        <span className="min-w-0 flex-1 truncate">No next action here</span>
        <button
          type="button"
          onClick={() => onSetNextAction(position, null)}
          className="flex-none font-semibold text-u-accent hover:underline"
        >
          Set next action
        </button>
      </div>
    );
  }

  return (
    <div className={cn(foot, "bg-u-sunken")}>
      <Popover
        width={380}
        hasPopup="dialog"
        label={`Next action on ${position.positionTitle ?? "this position"}: ${first.title}. ${open.length} open — show all`}
        triggerClassName="flex h-full min-w-0 flex-1 items-center gap-2 text-start"
        trigger={(isOpen) => (
          <>
            <Icon d={taskKindOf(first.kind).icon} size={13} className="flex-none text-u-text3" />
            <span className="min-w-0 truncate font-medium text-u-text">{first.title}</span>
            <DueText dueAt={first.dueAt} className="flex-none font-mono text-meta font-semibold" />
            <span className="ms-auto flex flex-none items-center gap-1.5">
              <Avatar id={first.assignee.userId} name={first.assignee.fullName ?? "Someone"} src={first.assignee.avatarUrl} size="xs" />
              {open.length > 1 && (
                <span className="rounded-full bg-u-raised px-1.5 font-mono text-meta font-semibold text-u-text2">
                  +{open.length - 1}
                </span>
              )}
              <Icon d={ICONS.chevronDown} size={13} className={cn("text-u-text3 transition-transform", isOpen && "rotate-180")} />
            </span>
          </>
        )}
      >
        {(close) => (
          <NextActionsPanel
            tasks={tasks.data}
            projectId={position.projectId}
            candidateId={position.candidateId}
            completion={completion}
            onClose={close}
            onOpen={(taskId) => {
              close();
              onSetNextAction(position, taskId);
            }}
          />
        )}
      </Popover>
    </div>
  );
}
