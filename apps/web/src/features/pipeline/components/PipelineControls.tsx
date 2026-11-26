import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { ClientHiddenReason } from "../api/types";

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

/**
 * The switch locked off with the reason beside it (`PositionPipeline.dc.html`, #1488): a refusal, a withdrawal or a
 * restriction hides someone from the client whatever the team sets, until they say yes or it is lifted.
 */
export function HiddenFromClient({
  reason,
  name,
  clientName,
}: {
  reason: ClientHiddenReason;
  name: string;
  clientName: string;
}) {
  const title = hiddenTitleOf(reason, clientName);
  return (
    <span className="flex min-w-0 items-center gap-1.5">
      <button
        type="button"
        role="switch"
        aria-checked={false}
        aria-disabled
        aria-label={`Visible to client: ${name}. ${title}`}
        title={title}
        onClick={(event) => event.stopPropagation()}
        className="grid size-[22px] flex-none cursor-not-allowed place-items-center rounded-u-control opacity-45"
      >
        <Icon d={ICONS.eyeOff} size={14} className="flex-none text-u-text3" />
      </button>
      <span
        title={title}
        className="min-w-0 truncate rounded-u-control bg-u-offlimits-tint px-1.5 py-0.5 text-eyebrow font-semibold text-u-offlimits"
      >
        Hidden from {clientName}
      </span>
    </span>
  );
}

function hiddenTitleOf(reason: ClientHiddenReason, clientName: string): string {
  switch (reason) {
    case "share_declined":
      return `They said no to being shared with ${clientName}. They stay hidden until they say yes.`;
    case "share_withdrawn":
      return `They withdrew their consent to be shared with ${clientName}. They stay hidden until they say yes.`;
    case "processing_restricted":
      return `Processing is restricted. They're hidden from ${clientName} until it's lifted.`;
  }
}
