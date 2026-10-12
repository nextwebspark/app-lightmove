import type { ReactNode } from "react";
import { Button } from "./index";
import { Modal } from "./Modal";

/**
 * The one question asked before an action that cannot be taken back: the title names the object, the
 * body the consequence, and the confirm button is the verb itself ("Stop sequence", never "OK").
 * A reversible action does not come here — it acts at once and offers Undo. `tone="primary"` is for one
 * that destroys nothing but is too large to take back by hand, such as adding every company a filter finds.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  pending = false,
  confirmDisabled = false,
  tone = "danger",
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  /** What happens, and to whom. */
  children: ReactNode;
  confirmLabel: string;
  pending?: boolean;
  /** The action cannot go ahead as things stand; the body says why. */
  confirmDisabled?: boolean;
  tone?: "danger" | "primary";
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
          <Button variant={tone} loading={pending} disabled={confirmDisabled} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-2 text-body text-u-text2">{children}</div>
    </Modal>
  );
}
