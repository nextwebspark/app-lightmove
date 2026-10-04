import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { copyText } from "../../../lib/clipboard";

/** A value an admin hands to someone else — a redirect URI, an approval link — shown whole, with a Copy beside it. */
export function CopyableValue({ label, value }: { label: string; value: string }) {
  const toast = useToast();

  const handleCopy = async () => {
    toast((await copyText(value)) ? `${label} copied` : "Couldn't copy — select it and copy it yourself");
  };

  return (
    <div>
      <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">{label}</div>
      <div className="flex items-start gap-2">
        <code className="min-w-0 flex-1 rounded-[6px] border border-u-border bg-u-surface px-2.5 py-2 font-mono text-note text-u-text2 [overflow-wrap:anywhere]">
          {value}
        </code>
        <button
          type="button"
          onClick={handleCopy}
          aria-label={`Copy ${label.toLowerCase()}`}
          className="flex flex-none items-center gap-1.5 rounded-[6px] border border-u-border-strong bg-u-surface px-2.5 py-2 text-note text-u-text2 transition hover:text-u-text"
        >
          <Icon d={ICONS.copy} size={13} />
          Copy
        </button>
      </div>
    </div>
  );
}
