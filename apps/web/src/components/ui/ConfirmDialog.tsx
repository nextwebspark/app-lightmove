import type { ReactNode } from "react";
import { Button } from "./index";
import { Modal } from "./Modal";

/**
 * The one question asked before an action that cannot be taken back: the title names the object, the
 * body the consequence, and the confirm button is the verb itself ("Stop sequence", never "OK").
 * A reversible action does not come here — it acts at once and offers Undo.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  pending = false,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  /** What happens, and to whom. */
  children: ReactNode;
  confirmLabel: string;
  pending?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      dismissible={!pending}
      title={title}
      footer={
        <>
          <Button variant="secondary" disabled={pending} onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" loading={pending} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-2 text-body text-u-text2">{children}</div>
    </Modal>
  );
}
