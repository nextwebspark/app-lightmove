import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** Whether a client seat on the position sees this executive. */
export function VisibilityToggle({
  visible,
  name,
  compact = false,
  onToggle,
}: {
  visible: boolean;
  name: string;
  compact?: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={visible}
      aria-label={`Visible to client: ${name}`}
      title={visible ? "Visible to the client — click to make internal" : "Internal only — click to share with the client"}
      onClick={(event) => {
        event.stopPropagation();
        onToggle();
      }}
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap text-u-text2 hover:text-u-text",
        compact ? "text-eyebrow" : "text-meta",
      )}
    >
      <Icon
        d={visible ? ICONS.eye : ICONS.eyeOff}
        size={compact ? 12 : 14}
        className={visible ? "text-u-accent" : "text-u-text3"}
      />
      {compact ? (visible ? "Client visible" : "Internal only") : visible ? "Client" : "Internal"}
    </button>
  );
}
