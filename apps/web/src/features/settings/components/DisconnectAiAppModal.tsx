import { Button, Modal } from "../../../components/ui";
import type { OAuthGrant } from "../api/types";

export function DisconnectAiAppModal({
  grant,
  ownGrant,
  disconnecting,
  onConfirm,
  onClose,
}: {
  grant: OAuthGrant;
  ownGrant: boolean;
  disconnecting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const whose = ownGrant ? grant.clientName : `${grant.ownerName}'s ${grant.clientName}`;
  const asked = ownGrant ? "you will see" : `${grant.ownerName} will see`;
  return (
    <Modal
      open
      onClose={onClose}
      title={`Disconnect ${grant.clientName}?`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={disconnecting}
            onClick={onConfirm}
          >
            Disconnect
          </Button>
        </>
      }
    >
      <p className="text-[13px] text-u-text2">
        {whose} loses access at once; its next request is refused. What it already read cannot be taken back. It can
        ask to connect again, and {asked} the consent screen.
      </p>
    </Modal>
  );
}
