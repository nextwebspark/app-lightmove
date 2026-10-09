import { Button, Modal } from "../../../components/ui";
import type { ApiKey } from "../api/types";

export function RevokeApiKeyModal({
  apiKey,
  ownKey,
  revoking,
  onConfirm,
  onClose,
}: {
  apiKey: ApiKey;
  ownKey: boolean;
  revoking: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const whatStops =
    apiKey.kind === "SERVICE"
      ? "Every integration using this workspace key stops at once. This cannot be undone; create a new key to reconnect them."
      : `${whoseUse(apiKey, ownKey)} stops at once. This cannot be undone; create a new key to reconnect it.`;

  return (
    <Modal
      open
      onClose={onClose}
      title={`Revoke ${apiKey.name}?`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={revoking}
            onClick={onConfirm}
          >
            Revoke key
          </Button>
        </>
      }
    >
      <p className="text-[13px] text-u-text2">{whatStops}</p>
    </Modal>
  );
}

function whoseUse(apiKey: ApiKey, ownKey: boolean): string {
  if (ownKey || !apiKey.ownerName) return "Anything using this key";
  return `Anything using ${apiKey.ownerName}'s key`;
}
