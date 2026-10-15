import { cn } from "../../../lib/cn";

/** An action that asks the AI, in the AI's tint: Suggest with AI on Background, Assess with AI on Assessment. */
export function AiActionButton({
  label,
  busyLabel,
  isBusy,
  disabled = false,
  title,
  onClick,
  className,
}: {
  label: string;
  busyLabel: string;
  isBusy: boolean;
  disabled?: boolean;
  title?: string;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isBusy || disabled}
      title={title}
      aria-busy={isBusy}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md border border-u-inferred/40 bg-u-inferred-tint px-2 py-1 font-mono text-meta font-semibold text-u-inferred transition hover:border-u-inferred disabled:opacity-60",
        isBusy && "cursor-progress",
        className,
      )}
    >
      <span aria-hidden>✦</span>
      {isBusy ? busyLabel : label}
    </button>
  );
}
