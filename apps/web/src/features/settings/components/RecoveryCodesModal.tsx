import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal, useToast } from "../../../components/ui";
import { copyText } from "../../../lib/clipboard";
import { saveBlob } from "../../../lib/saveBlob";

/**
 * The one time a set of recovery codes is shown: only their hashes are kept. Only its own button closes it, so a stray
 * click cannot lose a set unseen, and the codes live in the caller's component state, never a query cache.
 */
export function RecoveryCodesModal({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const toast = useToast();
  const asText = codes.join("\n");

  const handleCopy = async () => {
    toast((await copyText(asText)) ? "Recovery codes copied" : "Couldn't copy — select them and copy them yourself");
  };

  const handleDownload = () =>
    saveBlob(
      new Blob([`Uncava recovery codes\nEach code works once.\n\n${asText}\n`], { type: "text/plain" }),
      "uncava-recovery-codes.txt",
    );

  return (
    <Modal
      open
      onClose={onDone}
      dismissible={false}
      title="Save your recovery codes"
      className="md:w-[480px]"
      footer={<Button onClick={onDone}>I've saved them</Button>}
    >
      <p className="mb-4 text-body text-u-text2">
        If you lose your phone, sign in with one of these instead of a code. Each works once.
      </p>
      <ul
        aria-label="Recovery codes"
        className="grid grid-cols-2 gap-x-4 gap-y-1.5 rounded-u-control border border-u-border-strong bg-u-raised px-4 py-3 font-mono text-body font-medium text-u-text"
      >
        {codes.map((code) => (
          <li key={code}>{code}</li>
        ))}
      </ul>
      <div className="mt-3 flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={handleCopy}>
          <Icon d={ICONS.copy} size={14} />
          Copy
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={handleDownload}>
          Download
        </Button>
      </div>
      <div className="mt-3 flex gap-2.5 rounded-lg border border-u-signal bg-u-signal-tint px-3 py-2.5 text-note text-u-text2">
        <Icon d={ICONS.warning} size={16} className="mt-px flex-none text-u-signal" />
        <span>
          <b className="text-u-text">You won't see these codes again.</b> Keep them somewhere safe, apart from your
          phone. Getting new codes stops these working.
        </span>
      </div>
    </Modal>
  );
}
