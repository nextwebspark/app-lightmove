import { cn } from "../../../lib/cn";

/** Live once anyone has been put on the sequence; a Draft until then. */
export function SequenceStatePill({ isLive }: { isLive: boolean }) {
  return (
    <span
      className={cn(
        "rounded-full px-2 py-[2px] font-mono text-[11px] font-medium",
        isLive ? "bg-u-direct-tint text-u-direct" : "bg-u-raised text-u-text3",
      )}
    >
      {isLive ? "Live" : "Draft"}
    </span>
  );
}
