import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { confirmationNameMatches } from "../../../lib/confirmationName";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail } from "../api/types";
import { isArchivedClient } from "../lib/filtering";

/** Archiving a record is the firm's own call: a workspace admin's, as `WORKSPACE_MANAGE` is. */
export function useMayArchiveClients(): boolean {
  const { user } = useAuth();
  return user?.workspace?.roles.includes("ADMIN") ?? false;
}

/** The foot of both record drawers: Archive for an admin — or Restore, once it is archived — then New position. */
export function ClientDrawerActions({
  client,
  onClose,
  onNewMandate,
}: {
  client: ClientDetail;
  onClose: () => void;
  onNewMandate: () => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const mayArchive = useMayArchiveClients();
  const restoring = useRestoreClient(client);
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const archived = isArchivedClient(client);
  if (archived && !mayArchive) return null;

  return (
    <div className="flex items-center gap-2 border-t border-u-border px-5 py-3">
      {mayArchive &&
        (archived ? (
          <Button variant="secondary" loading={restoring.isPending} onClick={() => restoring.mutate()}>
            Restore {vocabulary.unitLower}
          </Button>
        ) : (
          <Button variant="ghost" className="-ms-3.5" onClick={() => setConfirming(true)}>
            <Icon d={ICONS.archive} size={14} />
            Archive
          </Button>
        ))}
      <span className="flex-1" />
      {!archived && (
        <Button variant="secondary" onClick={onNewMandate}>
          ＋ New position
        </Button>
      )}

      {confirming && (
        <ArchiveClientDialog
          client={client}
          onClose={() => setConfirming(false)}
          onArchived={() => {
            setConfirming(false);
            toast.success(`${client.name} archived`);
            onClose();
          }}
        />
      )}
    </div>
  );
}

/**
 * The warning before a record is put away. While any of its positions is live it names them and goes no further —
 * archiving those is their leads' call — and otherwise asks for the name typed back.
 */
function ArchiveClientDialog({
  client,
  onClose,
  onArchived,
}: {
  client: ClientDetail;
  onClose: () => void;
  onArchived: () => void;
}) {
  const queryClient = useQueryClient();
  const vocabulary = useWorkspaceVocabulary();
  const [typed, setTyped] = useState("");
  const [error, setError] = useState<string | null>(null);
  const livePositions = client.mandates.filter((mandate) => !mandate.archivedAt);
  const archiving = useMutation({
    mutationFn: () => clientsApi.archiveClient(client.id, typed),
    onSuccess: () => {
      refreshAfterArchiving(queryClient);
      onArchived();
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ConfirmDialog
      open
      title={`Archive ${client.name}?`}
      confirmLabel={`Archive ${vocabulary.unitLower}`}
      pending={archiving.isPending}
      confirmDisabled={livePositions.length > 0 || !confirmationNameMatches(client.name, typed)}
      onConfirm={() => archiving.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      {livePositions.length > 0 ? (
        <>
          <p>
            {livePositions.length === 1 ? "1 position here is" : `${livePositions.length} positions here are`} still
            open. Each one's lead archives it first:
          </p>
          <ul className="flex list-disc flex-col gap-1 ps-5">
            {livePositions.map((position) => (
              <li key={position.id}>
                <Link to={`/projects/${position.id}`} className="font-medium text-u-text hover:underline">
                  {position.positionTitle}
                </Link>
                {position.leadName ? ` — ${position.leadName}` : ""}
              </li>
            ))}
          </ul>
        </>
      ) : (
        <>
          <ul className="flex list-disc flex-col gap-1.5 ps-5">
            <li>
              It leaves the {vocabulary.unitsLower} list and the New position picker, for the whole team.
            </li>
            <li>Its archived positions, {vocabulary.contactsLower} and history stay as they are.</li>
            <li>Nothing is deleted. An admin can restore it.</li>
          </ul>
          <p className="mt-2">
            Type <b className="font-semibold text-u-text">{client.name}</b> to confirm.
          </p>
          <Field label="Name to confirm">
            <Input value={typed} onChange={(event) => setTyped(event.target.value)} autoFocus />
          </Field>
        </>
      )}
    </ConfirmDialog>
  );
}

/** At the top of an archived record: who put it away and what that means. Restore is in the foot. */
export function ClientArchivedNotice({ client, className }: { client: ClientDetail; className?: string }) {
  const vocabulary = useWorkspaceVocabulary();
  if (!isArchivedClient(client)) return null;
  const when = formatInstantDate(client.archivedAt);
  const by = client.archivedByName ? ` by ${client.archivedByName}` : "";
  return (
    <div
      role="status"
      className={cn(
        "flex items-start gap-2.5 rounded-u-card border border-dashed border-u-border-strong bg-u-raised px-3.5 py-2.5 text-note text-u-text2",
        className,
      )}
    >
      <Icon d={ICONS.archive} size={15} className="mt-0.5 flex-none text-u-text3" />
      <span>
        <b className="font-semibold text-u-text">Archived{when ? ` ${when}` : ""}{by}.</b> Hidden from the{" "}
        {vocabulary.unitsLower} list and the New position picker.
      </span>
    </div>
  );
}

function useRestoreClient(client: ClientDetail) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: () => clientsApi.restoreClient(client.id),
    onSuccess: () => {
      refreshAfterArchiving(queryClient);
      toast.success(`${client.name} restored`);
    },
    onError: (failure) => toast.error(messageFor(failure)),
  });
}

/** The list and every record, one prefix: the record drawer reads `["clients", id]`. */
function refreshAfterArchiving(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
}
