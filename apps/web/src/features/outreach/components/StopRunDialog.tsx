import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";

/** Stopping a sequence cannot be undone: nothing more is sent and the run cannot be resumed. */
export function StopRunDialog({
  open,
  name,
  pending,
  onConfirm,
  onClose,
}: {
  open: boolean;
  /** Whose sequence, when it is known. */
  name: string | null;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <ConfirmDialog
      open={open}
      title={name ? `Stop ${name}'s sequence?` : "Stop this sequence?"}
      confirmLabel="Stop sequence"
      pending={pending}
      onConfirm={onConfirm}
      onClose={onClose}
    >
      <p>Remaining emails won't be sent. This can't be resumed.</p>
    </ConfirmDialog>
  );
}
