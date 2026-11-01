import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { PipelineRow } from "../api/types";

export type CardActionKind = "call" | "email" | "note" | "schedule";

/** Why an action a card offers cannot be taken now; null where it can. */
export interface CardActionsUnavailable {
  email: string | null;
  schedule: string | null;
}

const ACTION_BUTTON =
  "grid size-[22px] flex-none place-items-center rounded-u-control text-u-accent hover:bg-u-accent-tint";

/**
 * A card's quick actions (`Position.dc.html`'s board): Log call (the note composer, set to a call), Email, Add note and
 * Schedule, each opening its own dialog. Their clicks never open the card.
 */
export function PipelineCardActions({
  row,
  unavailable,
  onAction,
}: {
  row: PipelineRow;
  unavailable: CardActionsUnavailable;
  onAction: (kind: CardActionKind, row: PipelineRow) => void;
}) {
  return (
    <span className="ms-auto flex items-center gap-0.5" onClick={(event) => event.stopPropagation()}>
      <CardActionButton
        icon={ICONS.phone}
        label="Log call"
        unavailable={null}
        onPress={() => onAction("call", row)}
      />
      <CardActionButton
        icon={ICONS.mail}
        label="Email"
        unavailable={unavailable.email}
        onPress={() => onAction("email", row)}
      />
      <CardActionButton
        icon={ICONS.noteBubble}
        label="Add note"
        unavailable={null}
        onPress={() => onAction("note", row)}
      />
      <CardActionButton
        icon={ICONS.calendar}
        label="Schedule"
        unavailable={unavailable.schedule}
        onPress={() => onAction("schedule", row)}
      />
    </span>
  );
}

function CardActionButton({
  icon,
  label,
  unavailable,
  onPress,
}: {
  icon: string;
  label: string;
  unavailable: string | null;
  onPress: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={unavailable ?? label}
      aria-disabled={unavailable !== null || undefined}
      onClick={() => {
        if (unavailable === null) onPress();
      }}
      className={cn(ACTION_BUTTON, unavailable !== null && "cursor-not-allowed opacity-40 hover:bg-transparent")}
    >
      <Icon d={icon} size={14} />
    </button>
  );
}
