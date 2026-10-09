import { ConfirmDialog } from "../../../components/ui";

/** Stopping a sequence cannot be undone: nothing more is sent and the run cannot be resumed. */
export function StopRunDialog({
  name,
  pending,
  onConfirm,
  onClose,
}: {
  /** Whose sequence; null closes the dialog. */
  name: string | null;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <ConfirmDialog
      open={name !== null}
      title={`Stop ${name}'s sequence?`}
      confirmLabel="Stop sequence"
      pending={pending}
      onConfirm={onConfirm}
      onClose={onClose}
    >
      <p>Remaining emails won't be sent. This can't be resumed.</p>
    </ConfirmDialog>
  );
}
