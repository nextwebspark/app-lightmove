import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, Modal, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem, useDragReorder } from "../../../lib/useDragReorder";
import { useAuth } from "../../auth/AuthProvider";
import {
  defaultStatusOf,
  liveStages,
  liveStatuses,
  PIPELINE_COLOURS,
  pipelineSwatchClass,
} from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  PipelineCatalog,
  PipelineStage,
  PipelineStageChange,
  PipelineStatus,
  PipelineStatusChange,
} from "../../workspace/api/types";
import { usePipelineCatalog, usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";

const NAME_LIMIT = 40;

/**
 * Settings → Pipeline stages (`Settings.dc.html`, `pipeline-stages`): the workspace's stages in order, each
 * with its statuses. Every staff member reads it; only an admin changes it, and the server re-checks.
 */
export function PipelineStagesPage() {
  const { user } = useAuth();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const { query } = usePipelineCatalog();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [newStage, setNewStage] = useState("");
  const [blockedStage, setBlockedStage] = useState<PipelineStage | null>(null);

  const addStage = usePipelineWrite((label: string) => pipelineApi.createStage(label));
  const reorderStages = usePipelineWrite((ids: string[]) => pipelineApi.reorderStages(ids));

  const stages = query.data ? liveStages(query.data) : [];
  const activeIds = stages.filter((stage) => stage.kind === "active").map((stage) => stage.id);
  const stageOrder = useDragReorder(activeIds, (from, to) => {
    const closedIds = stages.filter((stage) => stage.kind === "closed").map((stage) => stage.id);
    const ids = [...moveItem(activeIds, from, to), ...closedIds];
    const previous = queryClient.getQueryData<PipelineCatalog>(pipelineApi.PIPELINE_CATALOG_KEY);
    if (previous) {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, {
        stages: [...previous.stages].sort((a, b) => rankIn(ids, a.id) - rankIn(ids, b.id)),
      });
    }
    reorderStages.mutate(ids, {
      onError: (error) => {
        if (previous) queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, previous);
        toast.error(messageFor(error));
      },
    });
  });

  const handleAddStage = (event: FormEvent) => {
    event.preventDefault();
    const label = newStage.trim();
    if (!label) return;
    addStage.mutate(label, {
      onSuccess: () => {
        setNewStage("");
        toast("Stage added to every position");
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  };

  return (
    <>
      <PageHeader
        title="Pipeline stages"
        subtitle="Stages are the columns of a position's pipeline; every status belongs to one stage. A candidate holds a status, and moves between stages by changing it. A retired status stays on the candidates who hold it but is never offered again."
      />
      {!isAdmin && (
        <p className="-mt-2 mb-4 inline-flex items-center gap-2 rounded-u-control border border-u-border bg-u-raised px-2.5 py-1.5 font-mono text-meta text-u-text2">
          <Icon d={ICONS.lock} size={12} />
          Only workspace admins can change stages and statuses.
        </p>
      )}

      {query.isError ? (
        <p className="text-note text-u-text3">{messageFor(query.error)}</p>
      ) : query.isPending ? (
        <CardsSkeleton />
      ) : (
        <div className="flex flex-col gap-3">
          {stages.map((stage) => (
            <StageCard
              key={stage.id}
              stage={stage}
              isAdmin={isAdmin}
              isDragging={stageOrder.draggingId === stage.id}
              dragProps={isAdmin && stage.kind === "active" ? stageOrder.rowProps(stage.id) : undefined}
              onHandleKeyDown={stageOrder.handleKeyDown(stage.id)}
              onRetireBlocked={() => setBlockedStage(stage)}
            />
          ))}
        </div>
      )}

      {isAdmin && query.isSuccess && (
        <>
          <form className="mt-3.5 flex flex-wrap items-center gap-2" onSubmit={handleAddStage}>
            <Input
              value={newStage}
              onChange={(event) => setNewStage(event.target.value)}
              placeholder="New stage, e.g. Assessed"
              aria-label="New stage"
              maxLength={NAME_LIMIT}
              className="max-w-[260px]"
            />
            <Button type="submit" size="sm" disabled={!newStage.trim()} loading={addStage.isPending}>
              <Icon d={ICONS.plus} size={14} />
              Add stage
            </Button>
          </form>
          <p className="mt-3 max-w-[620px] font-mono text-meta text-u-text3">
            A new stage is active and goes in before Declined, which stays last. A rename or a new colour reaches
            every position at once. Dropping a candidate on a stage&apos;s column gives them its default status.
          </p>
        </>
      )}

      <Modal
        open={blockedStage !== null}
        onClose={() => setBlockedStage(null)}
        title={`Retire ${blockedStage?.label ?? "stage"}`}
        footer={<Button onClick={() => setBlockedStage(null)}>OK</Button>}
      >
        <p className="text-body text-u-text2">
          Move or retire this stage&apos;s statuses first — a stage with live statuses can&apos;t be retired.
        </p>
      </Modal>
    </>
  );
}

function StageCard({
  stage,
  isAdmin,
  isDragging,
  dragProps,
  onHandleKeyDown,
  onRetireBlocked,
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  isDragging: boolean;
  dragProps?: ReturnType<ReturnType<typeof useDragReorder>["rowProps"]>;
  onHandleKeyDown: ReturnType<ReturnType<typeof useDragReorder>["handleKeyDown"]>;
  onRetireBlocked: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(stage.label);
  const [newStatus, setNewStatus] = useState("");
  const updateStage = usePipelineWrite((change: PipelineStageChange) => pipelineApi.updateStage(stage.id, change));
  const addStatus = usePipelineWrite((label: string) => pipelineApi.createStatus(stage.id, label));
  const reorderStatuses = usePipelineWrite((ids: string[]) => pipelineApi.reorderStatuses(stage.id, ids));
  const statuses = liveStatuses(stage);
  const defaultStatus = defaultStatusOf(stage);
  const isClosed = stage.kind === "closed";
  const statusIds = statuses.map((status) => status.id);

  const statusOrder = useDragReorder(statusIds, (from, to) => {
    const ids = moveItem(statusIds, from, to);
    const previous = queryClient.getQueryData<PipelineCatalog>(pipelineApi.PIPELINE_CATALOG_KEY);
    if (previous) {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, {
        stages: previous.stages.map((candidate) =>
          candidate.id === stage.id
            ? {
                ...candidate,
                statuses: [...candidate.statuses].sort((a, b) => rankIn(ids, a.id) - rankIn(ids, b.id)),
              }
            : candidate,
        ),
      });
    }
    reorderStatuses.mutate(ids, {
      onError: (error) => {
        if (previous) queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, previous);
        toast.error(messageFor(error));
      },
    });
  });

  const change = (patch: PipelineStageChange, success?: string) =>
    updateStage.mutate(patch, {
      onSuccess: () => {
        if (patch.label !== undefined) setRenaming(false);
        if (success) toast(success);
      },
      onError: (error) => toast.error(messageFor(error)),
    });

  const handleRetire = () => {
    if (statuses.length > 0) {
      onRetireBlocked();
      return;
    }
    change({ retired: true }, `${stage.label} retired`);
  };

  const handleAddStatus = (event: FormEvent) => {
    event.preventDefault();
    const label = newStatus.trim();
    if (!label) return;
    addStatus.mutate(label, {
      onSuccess: () => {
        setNewStatus("");
        toast("Status added");
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  };

  return (
    <section
      aria-label={stage.label}
      {...dragProps}
      className={cn("overflow-hidden rounded-u-card border border-u-border bg-u-surface", isDragging && "opacity-45")}
    >
      <div className="flex flex-wrap items-center gap-2.5 border-b border-u-border px-4 py-3">
        {isAdmin && !isClosed && <DragHandle label={`Reorder ${stage.label} stage`} onKeyDown={onHandleKeyDown} />}
        {isAdmin && isClosed && (
          <span title="The closed stage stays last" className="grid w-3.5 place-items-center text-u-text3">
            <Icon d={ICONS.lock} size={12} />
          </span>
        )}
        <ColourDot
          colour={stage.colour}
          name={`${stage.label} stage`}
          size="stage"
          editable={isAdmin}
          onChange={(colour) => change({ colour })}
        />
        {renaming ? (
          <RenameForm
            id={`rename-stage-${stage.id}`}
            label="Stage name"
            draft={draft}
            onDraft={setDraft}
            onCancel={() => setRenaming(false)}
            onSave={() => change({ label: draft.trim() }, "Renamed on every position")}
          />
        ) : (
          <span className="text-body font-semibold text-u-text">{stage.label}</span>
        )}
        <span
          className={cn(
            "rounded-full px-2 py-0.5 font-mono text-meta font-medium",
            isClosed ? "bg-u-raised text-u-text3" : "bg-u-accent-tint text-u-accent",
          )}
        >
          {isClosed ? "Closed" : "Active"}
        </span>
        <span className="font-mono text-meta text-u-text3">
          {statuses.length === 1 ? "1 status" : `${statuses.length} statuses`}
        </span>
        {isAdmin && (
          <span className="ms-auto flex gap-1">
            {/* Keyed apart: patched in place, the pressed Rename became this form's Save and submitted it mid-click. */}
            {renaming ? (
              <Button
                key="save"
                type="submit"
                form={`rename-stage-${stage.id}`}
                variant="secondary"
                size="xs"
                disabled={!draft.trim()}
                loading={updateStage.isPending}
              >
                Save
              </Button>
            ) : (
              <Button
                key="rename"
                type="button"
                variant="ghost"
                size="xs"
                onClick={() => {
                  setDraft(stage.label);
                  setRenaming(true);
                }}
              >
                Rename
              </Button>
            )}
            {!isClosed && (
              <Button
                variant="ghost"
                size="xs"
                title="Retire this stage"
                aria-label={`Retire ${stage.label} stage`}
                disabled={updateStage.isPending}
                onClick={handleRetire}
              >
                <Icon d={ICONS.archive} size={14} />
              </Button>
            )}
          </span>
        )}
      </div>

      <ul className="px-4 ps-10">
        {statuses.length === 0 && (
          <li className="py-3 font-mono text-meta text-u-text3">
            No statuses yet. The first one you add becomes the default.
          </li>
        )}
        {statuses.map((status) => (
          <StatusRow
            key={status.id}
            status={status}
            isDefault={defaultStatus?.id === status.id}
            isAdmin={isAdmin}
            isDragging={statusOrder.draggingId === status.id}
            dragProps={isAdmin ? statusOrder.rowProps(status.id) : undefined}
            onHandleKeyDown={statusOrder.handleKeyDown(status.id)}
          />
        ))}
      </ul>

      {isAdmin && (
        <form
          className="flex flex-wrap items-center gap-2 border-t border-u-border px-4 py-2.5 ps-10"
          onSubmit={handleAddStatus}
        >
          <Input
            value={newStatus}
            onChange={(event) => setNewStatus(event.target.value)}
            placeholder={`New status in ${stage.label}`}
            aria-label={`New status in ${stage.label}`}
            maxLength={NAME_LIMIT}
            className="max-w-[240px] py-1.5"
          />
          <Button type="submit" variant="secondary" size="xs" disabled={!newStatus.trim()} loading={addStatus.isPending}>
            <Icon d={ICONS.plus} size={12} />
            Add status
          </Button>
        </form>
      )}
    </section>
  );
}

function StatusRow({
  status,
  isDefault,
  isAdmin,
  isDragging,
  dragProps,
  onHandleKeyDown,
}: {
  status: PipelineStatus;
  isDefault: boolean;
  isAdmin: boolean;
  isDragging: boolean;
  dragProps?: ReturnType<ReturnType<typeof useDragReorder>["rowProps"]>;
  onHandleKeyDown: ReturnType<ReturnType<typeof useDragReorder>["handleKeyDown"]>;
}) {
  const toast = useToast();
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(status.label);
  const update = usePipelineWrite((change: PipelineStatusChange) => pipelineApi.updateStatus(status.id, change));

  const change = (patch: PipelineStatusChange, success?: string) =>
    update.mutate(patch, {
      onSuccess: () => {
        if (patch.label !== undefined) setRenaming(false);
        if (success) toast(success);
      },
      onError: (error) => toast.error(messageFor(error)),
    });

  return (
    <li
      aria-label={status.label}
      {...dragProps}
      className={cn(
        "flex flex-wrap items-center gap-2.5 border-t border-u-border py-2 first:border-t-0",
        isDragging && "opacity-45",
      )}
    >
      {isAdmin && <DragHandle label={`Reorder ${status.label}`} onKeyDown={onHandleKeyDown} />}
      <ColourDot
        colour={status.colour}
        name={status.label}
        size="status"
        editable={isAdmin}
        onChange={(colour) => change({ colour })}
      />
      {renaming ? (
        <RenameForm
          id={`rename-status-${status.id}`}
          label="Status name"
          draft={draft}
          onDraft={setDraft}
          onCancel={() => setRenaming(false)}
          onSave={() => change({ label: draft.trim() }, "Renamed on every position")}
        />
      ) : (
        <span className="text-note text-u-text">{status.label}</span>
      )}
      {isDefault && (
        <span className="rounded-sm bg-u-accent-tint px-1.5 py-px font-mono text-eyebrow font-medium text-u-accent">
          Default
        </span>
      )}
      {status.system && (
        <span
          title="Built-in status — can be renamed, not retired"
          className="inline-flex items-center gap-1 rounded-sm bg-u-raised px-1.5 py-px font-mono text-eyebrow font-medium text-u-text3"
        >
          <Icon d={ICONS.lock} size={10} />
          System
        </span>
      )}
      {isAdmin && (
        <span className="ms-auto flex items-center gap-1">
          {!isDefault && (
            <Button
              variant="ghost"
              size="xs"
              disabled={update.isPending}
              onClick={() => change({ isDefault: true }, "Default status changed")}
            >
              Make default
            </Button>
          )}
          {/* Keyed apart: patched in place, the pressed Rename became this form's Save and submitted it mid-click. */}
          {renaming ? (
            <Button
              key="save"
              type="submit"
              form={`rename-status-${status.id}`}
              variant="secondary"
              size="xs"
              disabled={!draft.trim()}
              loading={update.isPending}
            >
              Save
            </Button>
          ) : (
            <Button
              key="rename"
              type="button"
              variant="ghost"
              size="xs"
              onClick={() => {
                setDraft(status.label);
                setRenaming(true);
              }}
            >
              Rename
            </Button>
          )}
          {status.system ? (
            <span title="Built-in status — can be renamed, not retired" className="grid w-7 place-items-center text-u-text3">
              <Icon d={ICONS.lock} size={12} />
            </span>
          ) : (
            <Button
              variant="ghost"
              size="xs"
              title="Stop offering this status; candidates keep it"
              aria-label={`Retire ${status.label}`}
              disabled={update.isPending}
              onClick={() => change({ retired: true }, "Status retired; candidates who hold it keep it")}
            >
              <Icon d={ICONS.archive} size={13} />
            </Button>
          )}
        </span>
      )}
    </li>
  );
}

function RenameForm({
  id,
  label,
  draft,
  onDraft,
  onCancel,
  onSave,
}: {
  id: string;
  label: string;
  draft: string;
  onDraft: (value: string) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <form
      id={id}
      className="contents"
      onSubmit={(event) => {
        event.preventDefault();
        if (draft.trim()) onSave();
      }}
    >
      <Input
        value={draft}
        onChange={(event) => onDraft(event.target.value)}
        onKeyDown={(event) => event.key === "Escape" && onCancel()}
        aria-label={label}
        maxLength={NAME_LIMIT}
        autoFocus
        className="max-w-[220px] py-1"
      />
    </form>
  );
}

/** A click moves the swatch on to the next colour, the mockup's way of recolouring in place. */
function ColourDot({
  colour,
  name,
  size,
  editable,
  onChange,
}: {
  colour: string;
  name: string;
  size: "stage" | "status";
  editable: boolean;
  onChange: (colour: string) => void;
}) {
  const index = PIPELINE_COLOURS.findIndex((candidate) => candidate.value === colour);
  const next = PIPELINE_COLOURS[(index + 1) % PIPELINE_COLOURS.length].value;
  return (
    <button
      type="button"
      disabled={!editable}
      title={editable ? "Change colour" : undefined}
      aria-label={`Colour of ${name}`}
      onClick={() => onChange(next)}
      className={cn(
        "flex-none rounded-full disabled:cursor-default",
        size === "stage" ? "size-[11px]" : "size-[9px]",
        pipelineSwatchClass(colour),
      )}
    />
  );
}

function DragHandle({ label, onKeyDown }: { label: string; onKeyDown: (event: KeyboardEvent<HTMLElement>) => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      title="Drag to reorder, or focus and use the arrow keys"
      onKeyDown={onKeyDown}
      className="grid w-3.5 cursor-grab place-items-center text-u-text3 hover:text-u-text2"
    >
      <Icon d={ICONS.dragHandle} size={13} />
    </button>
  );
}

function rankIn(ids: readonly string[], id: string): number {
  const index = ids.indexOf(id);
  return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}
