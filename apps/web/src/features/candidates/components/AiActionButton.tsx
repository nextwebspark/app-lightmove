import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";

interface AiActionButtonProps {
  label: string;
  isBusy: boolean;
  disabled?: boolean;
  /** The id of visible text that says why the button is disabled, or what it will do. */
  describedBy?: string;
  onClick: () => void;
}

/** An action that asks the AI, in the AI's tint: Suggest with AI on Background, Assess with AI on Assessment. */
export function AiActionButton({ label, isBusy, disabled = false, describedBy, onClick }: AiActionButtonProps) {
  return (
    <Button
      type="button"
      variant="secondary"
      size="xs"
      loading={isBusy}
      disabled={disabled}
      aria-describedby={describedBy}
      onClick={onClick}
      className={cn(
        "border-u-inferred/40 bg-u-inferred-tint py-1 font-mono font-semibold text-u-inferred",
        "hover:border-u-inferred hover:text-u-inferred",
      )}
    >
      {!isBusy && <Icon d={ICONS.sparkle} size={12} />}
      {label}
    </Button>
  );
}
