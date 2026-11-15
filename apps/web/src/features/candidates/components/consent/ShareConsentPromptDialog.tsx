import { formatInstantDate } from "../../../../lib/datetime";
import { Button } from "../../../../components/ui";
import { Modal } from "../../../../components/ui/Modal";
import type { ShareConsentRow } from "../../api/shareConsentApi";
import { useWorkspaceVocabulary } from "../../../workspace/lib/vocabulary";

/**
 * Turning client visibility on for someone with no consent to share (`Position.dc.html`, `sharePrompt`): warn, never
 * block. Visibility starts on, so nothing here claims nothing was shared.
 */
export function ShareConsentPromptDialog({
  fullName,
  clientName,
  row,
  busy,
  onAskFirst,
  onShowAnyway,
  onClose,
}: {
  fullName: string;
  clientName: string;
  row: ShareConsentRow | undefined;
  busy?: boolean;
  onAskFirst: () => void;
  onShowAnyway: () => void;
  onClose: () => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const asked = row?.requestOpenUntil && Date.parse(row.requestOpenUntil) > Date.now();
  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!busy}
      title={`No consent to share with ${clientName} yet`}
      footer={
        <>
          <Button variant="secondary" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button variant="secondary" disabled={busy} onClick={onShowAnyway}>
            Show anyway
          </Button>
          <Button loading={busy} onClick={onAskFirst}>
            Ask first
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-2 text-body text-u-text2">
        <p>
          {asked
            ? `${fullName} was asked on ${formatInstantDate(row?.requestedAt ?? null) ?? ""} whether you may share their profile with ${clientName} for this position, and hasn't answered yet.`
            : `${fullName} hasn't agreed to their profile being shared with ${clientName} for this position.`}
        </p>
        <p>Ask first emails them a request; they stay internal until you show them. Show anyway shows them to the {vocabulary.unitLower} now.</p>
      </div>
    </Modal>
  );
}
