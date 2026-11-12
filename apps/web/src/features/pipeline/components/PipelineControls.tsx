import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** Whether a client seat on the position sees this executive: an eye, open or struck through. */
export function VisibilityToggle({ visible, name, onToggle }: { visible: boolean; name: string; onToggle: () => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={visible}
      aria-label={`Visible to client: ${name}`}
      title={visible ? "Client visible" : "Internal only"}
      onClick={(event) => {
        event.stopPropagation();
        onToggle();
      }}
      className="grid size-[22px] flex-none place-items-center rounded-u-control hover:bg-u-border"
    >
      <Icon
        d={visible ? ICONS.eye : ICONS.eyeOff}
        size={14}
        className={cn("flex-none", visible ? "text-u-direct" : "text-u-text3")}
      />
    </button>
  );
}
