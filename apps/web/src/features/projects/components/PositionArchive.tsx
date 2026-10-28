import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { Popover } from "../../../components/ui/Popover";
import { confirmationNameMatches } from "../../../lib/confirmationName";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import { CLIENTS_KEY } from "../../clients/api/clientsApi";
import { POOL_KEY } from "../../candidates/api/poolApi";
import { NOTIFICATIONS_KEY_PREFIX } from "../../notifications/api/notificationsApi";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { canArchivePosition } from "../lib/access";
import { isArchived } from "../lib/filtering";

const MORE_BUTTON =
  "grid flex-none place-items-center rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-text";
const MENU_ITEM =
  "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-start text-note text-u-offlimits hover:bg-u-raised";

/** The ⋯ beside a position's header controls, drawn only for its lead: what is done to the position itself. */
export function PositionActionsMenu({ project, onArchive }: { project: Project; onArchive: () => void }) {
  const { user } = useAuth();
  if (isArchived(project) || !canArchivePosition(project, user?.id)) return null;
  return (
    <Popover
      label={`More for ${project.positionTitle}`}
      align="right"
      width={220}
      hasPopup="menu"
      triggerClassName={MORE_BUTTON}
      trigger={() => <Icon d={ICONS.more} size={16} />}
    >
      {(close) => (
        <div role="menu" aria-label={`${project.positionTitle} actions`}>
          <button
            type="button"
            role="menuitem"
            className={MENU_ITEM}
            onClick={() => {
              close();
              onArchive();
            }}
          >
            <Icon d={ICONS.archive} size={13} />
            Archive position…
          </button>
        </div>
      )}
    </Popover>
  );
}

/**
 * The warning before a position is put away: what it stops, what it hides, what it keeps — read from the server
 * so the outreach count is the one about to stop — and its title typed back before the button wakes.
 */
export function ArchivePositionDialog({
  project,
  onClose,
  onArchived,
}: {
  project: Project;
  onClose: () => void;
  onArchived: () => void;
}) {
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState("");
  const [error, setError] = useState<string | null>(null);
  const preview = useQuery({
    queryKey: projectsApi.archivePreviewKey(project.id),
    queryFn: () => projectsApi.archivePreview(project.id),
    staleTime: 0,
  });
  const archiving = useMutation({
    mutationFn: () => projectsApi.archiveProject(project.id, typed),
    onSuccess: () => {
      refreshAfterArchiving(queryClient, project.id);
      onArchived();
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  const runs = preview.data?.liveOutreachRuns ?? 0;
  const tasks = preview.data?.openTasks ?? 0;

  return (
    <ConfirmDialog
      open
      title={`Archive ${project.positionTitle}?`}
      confirmLabel="Archive position"
      pending={archiving.isPending}
      confirmDisabled={preview.isPending || !confirmationNameMatches(project.positionTitle, typed)}
      onConfirm={() => archiving.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <ul className="flex list-disc flex-col gap-1.5 ps-5">
        <li>It leaves every list of positions, the pickers and the counts, for the whole team.</li>
        {runs > 0 && (
          <li>
            <b className="font-semibold text-u-text">
              {runs === 1 ? "1 outreach run stops" : `${runs} outreach runs stop`} now
            </b>{" "}
            and will not start again if it is restored.
          </li>
        )}
        {tasks > 0 && (
          <li>
            {tasks === 1 ? "1 open task leaves" : `${tasks} open tasks leave`} My tasks and notifications until it is
            restored.
          </li>
        )}
        <li>Nothing is deleted. Only its lead can restore it.</li>
      </ul>
      <p className="mt-2">
        Type <b className="font-semibold text-u-text">{project.positionTitle}</b> to confirm.
      </p>
      <Field label="Position title">
        <Input value={typed} onChange={(event) => setTyped(event.target.value)} autoFocus />
      </Field>
    </ConfirmDialog>
  );
}

/** One click: restoring hides nothing and stops nothing, so it asks nothing. */
export function useRestorePosition(project: Project) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: () => projectsApi.restoreProject(project.id),
    onSuccess: () => {
      refreshAfterArchiving(queryClient, project.id);
      toast.success(`${project.positionTitle} restored`);
    },
    onError: (failure) => toast.error(messageFor(failure)),
  });
}

/** Across the top of an archived position's every page: what it means, and Restore for its lead. */
export function PositionArchivedNotice({ project }: { project: Project }) {
  const { user } = useAuth();
  const restoring = useRestorePosition(project);
  const canRestore = canArchivePosition(project, user?.id);
  const when = formatInstantDate(project.archivedAt);
  const by = project.archivedByName ? ` by ${project.archivedByName}` : "";

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-u-card border border-dashed border-u-border-strong bg-u-raised px-3.5 py-2.5 text-note text-u-text2"
    >
      <Icon d={ICONS.archive} size={15} className="flex-none text-u-text3" />
      <span className="min-w-0 flex-1">
        <b className="font-semibold text-u-text">Archived{when ? ` ${when}` : ""}{by}.</b> Hidden from lists, and its
        outreach is stopped.{canRestore ? "" : " Only its lead can restore it."}
      </span>
      {canRestore && (
        <Button size="xs" variant="secondary" loading={restoring.isPending} onClick={() => restoring.mutate()}>
          Restore position
        </Button>
      )}
    </div>
  );
}

function refreshAfterArchiving(queryClient: QueryClient, projectId: string) {
  for (const queryKey of [
    projectsApi.PROJECTS_KEY,
    CLIENTS_KEY,
    TASKS_KEY_PREFIX,
    NOTIFICATIONS_KEY_PREFIX,
    POOL_KEY,
    ["outreach", projectId],
  ]) {
    void queryClient.invalidateQueries({ queryKey });
  }
}
