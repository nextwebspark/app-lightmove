import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal, useToast } from "../../../components/ui";
import { copyText } from "../../../lib/clipboard";

/** The one time a key's secret is shown; only its own button closes it, so a stray click cannot lose a key unseen. */
export function RevealApiKeyModal({ name, secret, onDone }: { name: string; secret: string; onDone: () => void }) {
  const toast = useToast();

  const handleCopy = async () => {
    toast((await copyText(secret)) ? "Key copied" : "Couldn't copy — select it and copy it yourself");
  };

  return (
    <Modal
      open
      onClose={onDone}
      dismissible={false}
      title="Copy your API key"
      className="md:w-[520px]"
      footer={<Button onClick={onDone}>I've copied it</Button>}
    >
      <p className="mb-4 text-[13px] text-u-text2">{name} is ready. Paste it into the tool that will use it.</p>
      <div className="flex gap-2">
        <code
          aria-label="API key"
          className="min-w-0 flex-1 rounded-[6px] border border-u-border-strong bg-u-raised px-3 py-2.5 font-mono text-[12.5px] font-medium text-u-text [overflow-wrap:anywhere]"
        >
          {secret}
        </code>
        <button
          type="button"
          onClick={handleCopy}
          className="flex flex-none items-center gap-1.5 self-start rounded-[6px] border border-u-border-strong bg-u-surface px-3 py-2 text-[12.5px] font-medium text-u-text2 hover:text-u-text"
        >
          <Icon d={ICONS.copy} size={14} />
          Copy
        </button>
      </div>
      <div className="mt-3 flex gap-2.5 rounded-lg border border-u-signal bg-u-signal-tint px-3 py-2.5 text-[12px]/[1.55] text-u-text2">
        <Icon d={ICONS.warning} size={16} className="mt-px flex-none text-u-signal" />
        <span>
          <b className="text-u-text">You won't see this key again.</b> Uncava keeps only a fingerprint of it. If it is
          lost, revoke it and create another.
        </span>
      </div>
      <span className="type-micro-label mb-1.5 mt-4 block font-mono text-u-text3">Try it</span>
      <pre className="overflow-x-auto rounded-[6px] border border-u-border bg-u-raised px-3 py-2.5 font-mono text-[11.5px]/[1.6] text-u-text2">
        {`curl ${window.location.origin}/api/v1/public/projects \\\n  -H "Authorization: Bearer $UNCAVA_API_KEY"`}
      </pre>
    </Modal>
  );
}
