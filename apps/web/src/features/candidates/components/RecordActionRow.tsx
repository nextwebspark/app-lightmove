import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, buttonClassName } from "../../../components/ui";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
import { Popover } from "../../../components/ui/Popover";
import type { PersonNoteKind } from "../api/types";
import { NoteKindMark } from "./NoteKindMark";

type LoggedKind = Extract<PersonNoteKind, "call" | "meeting" | "linkedin" | "whatsapp">;

const LOGS: readonly { kind: LoggedKind; label: string }[] = [
  { kind: "call", label: "Log call" },
  { kind: "meeting", label: "Log meeting" },
  { kind: "linkedin", label: "Log LinkedIn" },
  { kind: "whatsapp", label: "Log WhatsApp" },
];

/**
 * The record full page's actions under the header (`PositionExecutiveDrawer.dc.html`, page): what a researcher does
 * most, out of the peek's footer ⋯ and its Outreach tab, one click away. Write email shows why it is held.
 */
export function RecordActionRow({
  emailHeldBy,
  onWriteEmail,
  onAddNote,
  onLog,
  onBookCall,
  onAddToSequence,
  sequenceHeldBy,
  onSetNextAction,
  onRemove,
}: {
  /** Why Write email cannot be pressed (Do not contact, no address); absent when it can. */
  emailHeldBy?: string;
  /** Absent where outreach is not set up. */
  onWriteEmail?: () => void;
  onAddNote: () => void;
  onLog: (kind: LoggedKind) => void;
  onBookCall: () => void;
  /** Absent where outreach is not set up. */
  onAddToSequence?: () => void;
  sequenceHeldBy?: string | null;
  onSetNextAction: () => void;
  onRemove?: () => void;
}) {
  const closingThen = (close: () => void, act: () => void) => () => {
    close();
    act();
  };
  return (
    <div role="group" aria-label="Actions" className="mt-3.5 flex flex-wrap items-center gap-2">
      {onWriteEmail && (
        // A disabled button shows no tooltip, so the reason sits on its wrapper.
        <span title={emailHeldBy}>
          <Button size="xs" disabled={emailHeldBy !== undefined} onClick={onWriteEmail}>
            <Icon d={ICONS.mail} size={13} />
            Write email
          </Button>
        </span>
      )}
      <Button variant="secondary" size="xs" onClick={onAddNote}>
        <Icon d={ICONS.noteBubble} size={13} />
        Add note
      </Button>
      <Popover
        label="Log"
        hasPopup="menu"
        width={200}
        placement="auto"
        triggerClassName={buttonClassName("secondary", "gap-1.5 py-[6px] text-note aria-expanded:text-u-text")}
        trigger={() => (
          <>
            <Icon d={ICONS.phoneCall} size={13} />
            Log
            <Icon d={ICONS.chevronDown} size={12} className="text-u-text3" />
          </>
        )}
      >
        {(close) => (
          <div role="menu" aria-label="Log" className="flex flex-col">
            {LOGS.map(({ kind, label }) => (
              <button
                key={kind}
                type="button"
                role="menuitem"
                onClick={closingThen(close, () => onLog(kind))}
                className="flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start text-note text-u-text hover:bg-u-raised"
              >
                {kind === "linkedin" || kind === "whatsapp" ? (
                  <NoteKindMark kind={kind} size={13} />
                ) : (
                  <Icon d={kind === "call" ? ICONS.phoneCall : ICONS.video} size={13} className="opacity-80" />
                )}
                {label}
              </button>
            ))}
          </div>
        )}
      </Popover>
      <ActionMenu label="More actions" triggerClassName="size-8 border-u-border-strong bg-u-surface text-u-text2">
        {(close) => (
          <>
            <MenuItem icon={ICONS.calendar} onSelect={closingThen(close, onBookCall)}>
              Book a meeting
            </MenuItem>
            {onAddToSequence && (
              <MenuItem
                icon={ICONS.outreach}
                disabled={Boolean(sequenceHeldBy)}
                note={sequenceHeldBy ?? undefined}
                onSelect={closingThen(close, onAddToSequence)}
              >
                Add to sequence
              </MenuItem>
            )}
            <MenuItem icon={ICONS.checklist} onSelect={closingThen(close, onSetNextAction)}>
              Set next action
            </MenuItem>
            {onRemove && (
              <>
                <MenuSeparator />
                <MenuItem icon={ICONS.trash} tone="danger" onSelect={closingThen(close, onRemove)}>
                  Remove from mandate
                </MenuItem>
              </>
            )}
          </>
        )}
      </ActionMenu>
    </div>
  );
}
