import { useMutation, useQuery } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton, useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as tasksApi from "../api/tasksApi";
import type { TaskResponse } from "../api/types";
import { taskKindOf } from "../lib/taskKinds";
import { useTasksChanged } from "../lib/useTasksChanged";
import { AutoPill, DueText } from "./TaskBits";

const DONE_SHOWN = 5;

/**
 * The executive drawer's Next actions (`Position.dc.html`, `isNextActions`): every open task on them here,
 * soonest first — ticked done in place, opened to edit — then the latest done. Staff-only.
 */
export function NextActionsSection({
  projectId,
  candidateId,
  firstName,
  justCompleted,
  onCompleted,
  onOpen,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  /** A task was just ticked done here or in the modal: offer the next one. */
  justCompleted: boolean;
  onCompleted: () => void;
  /** Opens the modal on a task, or on a new one for null. */
  onOpen: (taskId: string | null) => void;
}) {
  const toast = useToast();
  const tasksChanged = useTasksChanged();
  const tasks = useQuery({
    queryKey: tasksApi.CANDIDATE_TASKS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => tasksApi.getCandidateTasks(projectId, candidateId, signal),
  });
  const complete = useMutation({
    mutationFn: (taskId: string) => tasksApi.updateTask(taskId, { status: "done" }),
    onSuccess: () => {
      tasksChanged(projectId);
      onCompleted();
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const open = tasks.data?.open ?? [];
  const done = (tasks.data?.done ?? []).slice(0, DONE_SHOWN);

  return (
    <section aria-label="Next actions" className="border-b border-u-border pb-3">
      <h3 className="type-micro-label flex items-center gap-2 pb-2.5 pt-4 font-mono text-u-text3">
        <Icon d={ICONS.calendar} size={13} />
        Next actions
        {open.length > 0 && <span className="rounded-sm bg-u-raised px-1.5">{open.length}</span>}
      </h3>
      {tasks.isError ? (
        <p className="text-note text-u-text3">{messageFor(tasks.error)}</p>
      ) : tasks.isPending ? (
        <LinesSkeleton lines={2} />
      ) : (
        <>
          {open.map((task) => (
            <OpenTask
              key={task.id}
              task={task}
              completing={complete.isPending && complete.variables === task.id}
              onComplete={() => complete.mutate(task.id)}
              onOpen={() => onOpen(task.id)}
            />
          ))}
          {open.length === 0 && (
            <p className="text-note text-u-text3">
              No next action for {firstName}. Every executive in play should have one.
            </p>
          )}
          {justCompleted && (
            <div className="mt-2 flex items-center gap-2 rounded-u-chip bg-u-direct-tint px-2.5 py-2 text-note text-u-text2">
              <Icon d={ICONS.check} size={14} className="flex-none text-u-direct" />
              <span className="flex-1">Done. Set the next one?</span>
              <button
                type="button"
                onClick={() => onOpen(null)}
                className="rounded-u-control border border-u-border-strong bg-u-surface px-2.5 py-0.5 text-note font-semibold text-u-text hover:border-u-text3"
              >
                Set next action
              </button>
            </div>
          )}
          <button
            type="button"
            onClick={() => onOpen(null)}
            className="mt-2 inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline"
          >
            <Icon d={ICONS.plus} size={13} />
            Add a next action
          </button>
          {done.length > 0 && (
            <div aria-label="Done" role="group">
              <div className="type-micro-label mb-1 mt-3 font-mono text-u-text3">Done</div>
              {done.map((task) => (
                <div key={task.id} className="flex items-center gap-2.5 py-1 text-note text-u-text3">
                  <Icon d={ICONS.check} size={14} className="flex-none text-u-direct" />
                  <span className="min-w-0 truncate line-through">{task.title}</span>
                  <span className="flex-none font-mono text-meta">
                    Done {formatInstantDate(task.completedAt)}
                    {task.completedBy?.fullName && ` by ${task.completedBy.fullName}`}
                  </span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}

function OpenTask({
  task,
  completing,
  onComplete,
  onOpen,
}: {
  task: TaskResponse;
  completing: boolean;
  onComplete: () => void;
  onOpen: () => void;
}) {
  const { user } = useAuth();
  const assigneeName = task.assignee.fullName ?? "Someone";
  return (
    <div className="flex items-start gap-2.5 border-b border-u-border py-2">
      <button
        type="button"
        role="checkbox"
        aria-checked={false}
        aria-label={`Mark ${task.title} done`}
        disabled={completing}
        onClick={onComplete}
        className="mt-px size-[18px] flex-none rounded-full border-[1.5px] border-u-border-strong transition hover:border-u-direct disabled:opacity-50"
      />
      <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-start">
        <span className="flex min-w-0 items-center gap-1.5">
          <Icon d={taskKindOf(task.kind).icon} size={13} className="flex-none text-u-text3" />
          <span className="truncate text-body font-medium text-u-text">{task.title}</span>
          {task.source === "rule" && <AutoPill />}
        </span>
        {task.detail && <span className="mt-0.5 block truncate text-note text-u-text3">{task.detail}</span>}
        <span className="mt-1 flex items-center gap-1.5">
          <DueText dueAt={task.dueAt} className="font-mono text-meta font-semibold" />
          {task.dueAt && <span className="text-meta text-u-text3">·</span>}
          <Avatar id={task.assignee.userId} name={assigneeName} src={task.assignee.avatarUrl} size="xs" />
          <span className="text-meta text-u-text2">{task.assignee.userId === user?.id ? "Me" : assigneeName}</span>
        </span>
      </button>
    </div>
  );
}
