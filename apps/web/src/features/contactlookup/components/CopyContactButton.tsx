import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { copyText } from "../../../lib/clipboard";

export function CopyContactButton({
  what,
  value,
  size = 13,
  className,
}: {
  what: "Email" | "Phone";
  value: string;
  size?: number;
  className?: string;
}) {
  const toast = useToast();
  return (
    <button
      type="button"
      onClick={async () => toast((await copyText(value)) ? `${what} copied` : "Couldn't copy")}
      aria-label={`Copy ${what.toLowerCase()}`}
      title="Copy"
      className={cn(
        "flex flex-none rounded-md p-1 text-u-text3 transition hover:bg-u-raised hover:text-u-text",
        className,
      )}
    >
      <Icon d={ICONS.copy} size={size} />
    </button>
  );
}
