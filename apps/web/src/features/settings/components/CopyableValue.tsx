import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { copyText } from "../../../lib/clipboard";

/** A value an admin hands to someone else — a redirect URI, an approval link — shown whole, with a Copy beside it. */
export function CopyableValue({ label, value, showLabel = true }: { label: string; value: string; showLabel?: boolean }) {
  return (
    <div>
      {showLabel && <div className="mb-1.5 type-micro-label font-mono text-u-text3">{label}</div>}
      <div className="flex items-start gap-2">
        <code className="min-w-0 flex-1 rounded-u-control border border-u-border bg-u-surface px-2.5 py-2 font-mono text-note text-u-text2 [overflow-wrap:anywhere]">
          {value}
        </code>
        <CopyButton
          value={value}
          what={label}
          className="flex flex-none items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-2.5 py-2 text-note text-u-text2 transition hover:text-u-text"
        >
          <Icon d={ICONS.copy} size={13} />
          Copy
        </CopyButton>
      </div>
    </div>
  );
}

/** Copies `value` and says so: "Redirect URI copied". `what` also names the button, unless `label` does. */
export function CopyButton({
  value,
  what,
  label,
  className,
  children = "Copy",
}: {
  value: string;
  what: string;
  label?: string;
  className?: string;
  children?: ReactNode;
}) {
  const toast = useToast();

  const handleCopy = async () => {
    toast((await copyText(value)) ? `${what} copied` : "Couldn't copy — select it and copy it yourself");
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={label ?? `Copy ${what.charAt(0).toLowerCase()}${what.slice(1)}`}
      className={cn("cursor-pointer", className)}
    >
      {children}
    </button>
  );
}
