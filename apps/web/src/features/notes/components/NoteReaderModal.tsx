import { Button } from "../../../components/ui";
import { Modal } from "../../../components/ui/Modal";
import type { NoteFormat } from "../lib/noteFormat";
import { NoteText } from "./NoteText";
import { formatInstantDateTime } from "../../../lib/datetime";


/** A long note read whole, at a reading measure, in the middle of the screen; the card it came from stays as it was. */
export function NoteReaderModal({
  open,
  onClose,
  title,
  details = [],
  writtenAt,
  body,
  format,
  onEdit,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  /** What the card says of the note — who wrote it, what it is about — read before when. */
  details?: readonly (string | null | undefined)[];
  writtenAt: string;
  body: string;
  format: NoteFormat;
  /** Offered to whoever may change the note: closes the reader and edits it on its card. */
  onEdit?: () => void;
}) {
  const subtitle = [...details, formatInstantDateTime(writtenAt)].filter(Boolean).join(" · ");
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      subtitle={subtitle}
      closeButton
      className="md:w-[720px]"
      footer={
        <div className="flex w-full justify-end gap-2">
          {onEdit && (
            <Button type="button" variant="secondary" onClick={onEdit}>
              Edit
            </Button>
          )}
          <Button type="button" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <NoteText body={body} format={format} className="max-w-[62ch] text-body leading-[1.7] text-u-text2" />
    </Modal>
  );
}
