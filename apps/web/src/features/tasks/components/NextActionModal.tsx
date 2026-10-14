import { useMutation, useQuery } from "@tanstack/react-query";
import { Fragment, useId, useState, type MouseEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DateInput, Input, LinesSkeleton, Modal, useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDateTime } from "../../../lib/format";
import { useComboboxList } from "../../../lib/useComboboxList";
import { useAuth } from "../../auth/AuthProvider";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as tasksApi from "../api/tasksApi";
import type { TaskKind, TaskNote, TaskPreset, TaskResponse } from "../api/types";
import {
  defaultDueOf,
  dueInstantOf,
  localDueOf,
  presetDueLabel,
  presetDueOf,
  QUICK_DUES,
  quickDueOf,
  viewerTimeZone,
  type LocalDue,
} from "../lib/due";
import { linkedNoteLabel } from "../lib/linkedNote";
import { BELL_ICON, TASK_KINDS, taskKindOf } from "../lib/taskKinds";
import { firstNameOf, type TeammateOption } from "../lib/team";
import { usePositionTeam } from "../lib/usePositionTeam";
import { useTasksChanged } from "../lib/useTasksChanged";
import { AssigneePicker } from "./AssigneePicker";

/** Whose next action the modal sets, and the task it edits when there is one. */
export interface NextActionTarget {
  projectId: string;
  candidateId: string;
  executiveName: string;
  /** The person behind the row: their avatar's colour. */
  personId: string;
  taskId?: string | null;
  /** The note just saved that a new next action is set from: the composer's Save & set next action. */
  note?: TaskNote | null;
}

/**
 * Set or edit an executive's next action (`Position.dc.html`, "Next action"): what needs doing — a workspace
 * preset or free text — its kind, who on the position's team does it, and when. Editing adds Mark done and
 * Delete. Staff-only, like every task.
 */
export function NextActionModal({
  target,
  onClose,
  onCompleted,
}: {
  target: NextActionTarget | null;
  onClose: () => void;
  /** After Mark done, so the opener can offer the next one. */
  onCompleted?: () => void;
}) {
  if (!target) return null;
  return (
    <NextActionLoader
      key={`${target.candidateId}:${target.taskId ?? "new"}`}
      target={target}
      onClose={onClose}
      onCompleted={onCompleted}
    />
  );
}

function NextActionLoader({
  target,
  onClose,
  onCompleted,
}: {
  target: NextActionTarget;
  onClose: () => void;
  onCompleted?: () => void;
}) {
  const editing = Boolean(target.taskId);
  const heading = editing ? "Edit next action" : "Set next action";
  const tasks = useQuery({
    queryKey: tasksApi.CANDIDATE_TASKS_KEY(target.projectId, target.candidateId),
    queryFn: ({ signal }) => tasksApi.getCandidateTasks(target.projectId, target.candidateId, signal),
    enabled: editing,
  });
  const task = editing
    ? ([...(tasks.data?.open ?? []), ...(tasks.data?.done ?? [])].find((candidate) => candidate.id === target.taskId) ??
      null)
    : null;
  const team = usePositionTeam(target.projectId, task?.assignee);
  const presets = useQuery({
    queryKey: tasksApi.TASK_PRESETS_KEY,
    queryFn: ({ signal }) => tasksApi.getTaskPresets(signal),
  });

  if ((editing && tasks.isPending) || team.isLoading) {
    return (
      <Modal open onClose={onClose} title={heading} closeButton>
        <LinesSkeleton />
      </Modal>
    );
  }
  if (editing && !task) {
    return (
      <Modal open onClose={onClose} title={heading} closeButton>
        <p className="text-body text-u-text3">
          {tasks.isError ? messageFor(tasks.error) : "This next action is no longer open."}
        </p>
      </Modal>
    );
  }

  return (
    <NextActionForm
      target={target}
      task={task}
      heading={heading}
      positionTitle={team.project?.positionTitle ?? null}
      teammates={team.teammates}
      viewerId={team.viewerId}
      canManageTeam={team.canManageTeam}
      presets={(presets.data ?? []).filter((preset) => !preset.retired).sort((a, b) => a.position - b.position)}
      onClose={onClose}
      onCompleted={onCompleted}
    />
  );
}

type WhatOption = { kind: "free" } | { kind: "preset"; preset: TaskPreset };

function NextActionForm({
  target,
  task,
  heading,
  positionTitle,
  teammates,
  viewerId,
  canManageTeam,
  presets,
  onClose,
  onCompleted,
}: {
  target: NextActionTarget;
  task: TaskResponse | null;
  heading: string;
  positionTitle: string | null;
  teammates: TeammateOption[];
  viewerId: string | null;
  canManageTeam: boolean;
  presets: TaskPreset[];
  onClose: () => void;
  onCompleted?: () => void;
}) {
  const toast = useToast();
  const vocabulary = useWorkspaceVocabulary();
  const tasksChanged = useTasksChanged();
  const { user } = useAuth();
  const managesPresets = user?.workspace?.roles.includes("ADMIN") ?? false;
  const editing = task !== null;
  const readOnly = editing && !task.editable;
  const [title, setTitle] = useState(task?.title ?? "");
  const [presetId, setPresetId] = useState<string | null>(task?.presetId ?? null);
  const [kind, setKind] = useState<TaskKind>(task?.kind ?? "call");
  const [assigneeId, setAssigneeId] = useState(task?.assignee.userId ?? viewerId ?? "");
  const [due, setDue] = useState<LocalDue>(task?.dueAt ? localDueOf(task.dueAt) : defaultDueOf());
  const [detail, setDetail] = useState(task?.detail ?? "");
  const [error, setError] = useState<string | null>(null);
  const [listArmed, setListArmed] = useState(!editing);
  const listId = useId();

  const linkedNote = task ? task.note : (target.note ?? null);
  const assignee = teammates.find((option) => option.userId === assigneeId) ?? null;
  const dueAt = dueInstantOf(due);
  const reassigned = !editing || assigneeId !== task.assignee.userId;
  const notifies = assignee !== null && !assignee.isMe && reassigned;

  const query = title.trim().toLowerCase();
  const matches = presets.filter((preset) => !query || preset.label.toLowerCase().includes(query));
  const offersFree = query !== "" && !presets.some((preset) => preset.label.toLowerCase() === query);
  const options: WhatOption[] = [
    ...(offersFree ? [{ kind: "free" as const }] : []),
    ...matches.map((preset) => ({ kind: "preset" as const, preset })),
  ];

  const pickPreset = (preset: TaskPreset) => {
    setTitle(preset.label);
    setPresetId(preset.id);
    setKind(preset.kind);
    if (preset.dueOffsetDays !== null) setDue(presetDueOf(preset.dueOffsetDays));
  };
  const list = useComboboxList({
    optionCount: options.length,
    autoHighlightFirst: false,
    onCommit: (index) => {
      const option = options[index];
      if (option?.kind === "preset") pickPreset(option.preset);
      else setPresetId(null);
    },
  });
  const showList = list.open && !readOnly && (options.length > 0 || presets.length > 0);

  const succeed = (message: string) => {
    tasksChanged(target.projectId);
    toast(message);
    onClose();
  };
  const fail = (failure: unknown) => setError(messageFor(failure));

  const save = useMutation({
    mutationFn: () => {
      const change = {
        title: title.trim(),
        kind,
        assigneeUserId: assigneeId,
        dueAt: dueAt!,
        detail: detail.trim() || null,
      };
      return task
        ? tasksApi.updateTask(task.id, change)
        : tasksApi.createTask(target.projectId, target.candidateId, {
            ...change,
            presetId,
            noteId: target.note?.id ?? null,
          });
    },
    onSuccess: () => {
      if (notifies && assignee) succeed(`Assigned to ${assignee.fullName} · they've been notified`);
      else if (editing) succeed("Next action saved");
      else succeed(`Next action set · due ${formatInstantDateTime(dueAt)}`);
    },
    onError: fail,
  });
  const complete = useMutation({
    mutationFn: () => tasksApi.updateTask(task!.id, { status: "done" }),
    onSuccess: () => {
      onCompleted?.();
      succeed("Done · the timeline has a line for it");
    },
    onError: fail,
  });
  const remove = useMutation({
    mutationFn: () => tasksApi.cancelTask(task!.id),
    onSuccess: () => succeed("Task deleted"),
    onError: fail,
  });

  const busy = save.isPending || complete.isPending || remove.isPending;
  const canSave = !readOnly && !busy && title.trim().length > 0 && dueAt !== null && assignee !== null;
  const saveLabel = notifies && assignee ? `Save & notify ${firstNameOf(assignee.fullName)}` : "Save";

  return (
    <Modal
      open
      onClose={onClose}
      title={heading}
      closeButton
      className="md:w-[540px]"
      subtitle={
        <span className="flex items-center gap-1.5 font-sans text-note text-u-text3">
          <Avatar id={target.personId} name={target.executiveName} size="xs" />
          {target.executiveName}
          {positionTitle && ` · ${positionTitle}`}
        </span>
      }
      onSubmit={() => {
        if (canSave) save.mutate();
      }}
      footer={
        <>
          {editing && (
            <>
              <Button
                type="button"
                variant="secondary"
                className="text-u-direct hover:border-u-direct hover:text-u-direct"
                disabled={busy}
                loading={complete.isPending}
                onClick={() => complete.mutate()}
              >
                <Icon d={ICONS.check} size={14} />
                Mark done
              </Button>
              <button
                type="button"
                aria-label="Delete task"
                title="Delete task"
                disabled={readOnly || busy}
                onClick={() => remove.mutate()}
                className="grid size-10 flex-none place-items-center rounded-u-control border border-u-border-strong text-u-text3 transition hover:border-u-offlimits hover:text-u-offlimits disabled:opacity-50"
              >
                <Icon d={ICONS.trash} size={15} />
              </button>
            </>
          )}
          <span className="flex-1" />
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={!canSave} loading={save.isPending}>
            {saveLabel}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 pb-2">
        <div className="relative flex flex-col gap-1.5">
          <FieldLabel>What needs doing</FieldLabel>
          <Input
            role="combobox"
            aria-label="What needs doing"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && list.active >= 0 ? `${listId}-${list.active}` : undefined}
            autoComplete="off"
            maxLength={200}
            disabled={readOnly}
            value={title}
            placeholder="Pick a preset or type your own…"
            className="font-sans text-body"
            onChange={(event) => {
              setTitle(event.target.value);
              setPresetId(null);
              setListArmed(true);
              list.setActive(-1);
              list.setOpen(true);
            }}
            onClick={() => {
              setListArmed(true);
              list.setOpen(true);
            }}
            onFocus={() => {
              if (listArmed) list.inputHandlers.onFocus();
            }}
            onBlur={list.inputHandlers.onBlur}
            onKeyDown={list.inputHandlers.onKeyDown}
          />
          {showList && (
            <div
              onMouseDown={(event) => event.preventDefault()}
              className="absolute inset-x-0 top-full z-20 mt-1 max-h-72 overflow-y-auto rounded-u-chip border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
            >
              <ul id={listId} role="listbox" aria-label="Presets">
                {options.map((option, index) => {
                  const active = index === list.active;
                  const optionProps = {
                    id: `${listId}-${index}`,
                    role: "option" as const,
                    "aria-selected": active,
                    onMouseDown: (event: MouseEvent) => list.commitFromPointer(event, index),
                    onMouseEnter: () => list.setActive(index),
                    className: cn(
                      "flex cursor-pointer items-center gap-2 rounded-u-control px-2 py-2 text-body text-u-text",
                      active && "bg-u-raised",
                    ),
                  };
                  if (option.kind === "free") {
                    return (
                      <li key="free" {...optionProps} className={cn(optionProps.className, "mb-1 border-b border-u-border")}>
                        <Icon d={ICONS.pencil} size={13} className="flex-none text-u-text3" />
                        <span className="min-w-0 flex-1 truncate">Use “{title.trim()}”</span>
                        <span className="text-meta text-u-text3">Your own</span>
                      </li>
                    );
                  }
                  const { preset } = option;
                  const kindOption = taskKindOf(preset.kind);
                  const dueLabel = presetDueLabel(preset.dueOffsetDays);
                  const firstPreset = index === (offersFree ? 1 : 0);
                  return (
                    <Fragment key={preset.id}>
                      {firstPreset && (
                        <li role="presentation" className="type-micro-label px-2 pb-0.5 pt-1 font-mono text-u-text3">
                          Presets
                        </li>
                      )}
                      <li {...optionProps}>
                        <Icon d={kindOption.icon} size={13} className="flex-none text-u-text3" />
                        <span className="min-w-0 flex-1 truncate">{preset.label}</span>
                        <span className="flex-none text-meta text-u-text3">
                          {dueLabel ? `${kindOption.label} · ${dueLabel}` : kindOption.label}
                        </span>
                      </li>
                    </Fragment>
                  );
                })}
              </ul>
              {matches.length === 0 && (
                <p className="px-2 py-1.5 text-note text-u-text3">No preset matches. Your text is saved as written.</p>
              )}
              <p className="mt-1 border-t border-u-border px-2 pb-1 pt-1.5 text-meta text-u-text3">
                Presets are your workspace's
                {managesPresets && (
                  <>
                    {" · "}
                    <Link to="/settings/task-presets" className="text-u-text2 hover:text-u-text">
                      Manage in Settings
                    </Link>
                  </>
                )}
              </p>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <FieldLabel>Type</FieldLabel>
          <KindChips value={kind} onChange={setKind} disabled={readOnly} />
        </div>

        <div className="flex flex-col gap-1.5">
          <FieldLabel>Assign to</FieldLabel>
          <AssigneePicker
            label="Assign to"
            options={teammates}
            value={assigneeId}
            onChange={setAssigneeId}
            disabled={readOnly}
            footer={
              <>
                <span>This position's team only</span>
                {canManageTeam && (
                  <Link
                    to={`/projects/${target.projectId}/team`}
                    onClick={onClose}
                    className="font-medium text-u-accent hover:underline"
                  >
                    Not here? Add to team →
                  </Link>
                )}
              </>
            }
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <FieldLabel>Due</FieldLabel>
          <div className="grid grid-cols-[1fr_120px] gap-2.5">
            <DateInput
              ariaLabel="Due date"
              value={due.date}
              onChange={(date) => setDue((current) => ({ ...current, date }))}
            />
            <Input
              type="time"
              aria-label="Due time"
              disabled={readOnly}
              value={due.time}
              onChange={(event) => setDue((current) => ({ ...current, time: event.target.value }))}
            />
          </div>
          <div className="flex flex-wrap gap-1.5">
            {QUICK_DUES.map((quick) => {
              const quickDue = quickDueOf(quick.key);
              const on = quickDue.date === due.date && quickDue.time === due.time;
              return (
                <button
                  key={quick.key}
                  type="button"
                  aria-pressed={on}
                  disabled={readOnly}
                  onClick={() => setDue(quickDue)}
                  className={cn(
                    "rounded-full border px-2.5 py-0.5 text-meta font-medium transition",
                    on
                      ? "border-transparent bg-u-accent-tint text-u-accent"
                      : "border-u-border-strong text-u-text2 hover:text-u-text",
                  )}
                >
                  {quick.label}
                </button>
              );
            })}
          </div>
          {dueAt && (
            <span className="text-meta text-u-text2">
              Due {formatInstantDateTime(dueAt)} · {viewerTimeZone()}
            </span>
          )}
        </div>

        <label className="flex flex-col gap-1.5">
          <FieldLabel>
            Detail <span className="font-normal">Optional</span>
          </FieldLabel>
          <Input
            aria-label="Detail"
            maxLength={500}
            disabled={readOnly}
            value={detail}
            placeholder="e.g. Ask about notice period"
            className="font-sans"
            onChange={(event) => setDetail(event.target.value)}
          />
        </label>

        {notifies && assignee && (
          <p className="flex items-start gap-2 text-note text-u-text2">
            <Icon d={BELL_ICON} size={14} className="mt-0.5 flex-none" />
            <span>
              <b className="font-semibold text-u-text">{assignee.fullName}</b> is told in the app, and by email if
              it's still unread after 10 minutes.
            </span>
          </p>
        )}
        {readOnly && (
          <p className="text-note text-u-text3">
            Only whoever set this, whoever it's assigned to, or the lead can change it. Anyone on the team can mark it
            done.
          </p>
        )}
        {linkedNote && (
          <p className="flex items-center gap-2 text-meta text-u-text3">
            <Icon d={ICONS.fileText} size={13} className="flex-none" />
            {linkedNoteLabel(linkedNote)}
          </p>
        )}
        <p className="flex items-center gap-2 text-meta text-u-text3">
          <Icon d={ICONS.lock} size={13} className="flex-none" />
          Internal team only · {vocabulary.contacts} never see tasks
        </p>
        {error && (
          <p role="alert" className="text-note text-u-offlimits">
            {error}
          </p>
        )}
      </div>
    </Modal>
  );
}

function FieldLabel({ children }: { children: ReactNode }) {
  return <span className="text-meta font-medium text-u-text3">{children}</span>;
}

function KindChips({
  value,
  onChange,
  disabled,
}: {
  value: TaskKind;
  onChange: (kind: TaskKind) => void;
  disabled: boolean;
}) {
  const keys = useRadioGroupKeys(
    TASK_KINDS.map((option) => option.value),
    value,
    onChange,
  );
  return (
    <div ref={keys.ref} role="radiogroup" aria-label="Type" onKeyDown={keys.onKeyDown} className="flex flex-wrap gap-1.5">
      {TASK_KINDS.map((option) => {
        const on = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={on}
            tabIndex={on ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-u-control border px-2.5 py-1 text-note font-medium transition",
              on ? "border-transparent bg-u-accent-tint text-u-accent" : "border-u-border-strong text-u-text3 hover:text-u-text2",
            )}
          >
            <Icon d={option.icon} size={13} />
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
