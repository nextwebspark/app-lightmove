import { Icon, ICONS } from "../../../components/layout/Icon";
import { NoteKindMark } from "../../candidates/components/NoteKindMark";

type LoggedKind = "call" | "meeting" | "linkedin" | "whatsapp";

const KINDS: readonly { kind: LoggedKind; label: string; action: string }[] = [
  { kind: "call", label: "Call", action: "Log call" },
  { kind: "meeting", label: "Meeting", action: "Log meeting" },
  { kind: "linkedin", label: "LinkedIn", action: "Log LinkedIn" },
  { kind: "whatsapp", label: "WhatsApp", action: "Log WhatsApp" },
];

const LOG_CHIP =
  "inline-flex items-center gap-1.5 rounded-full border border-u-border bg-u-canvas px-2.5 py-0.5 text-note font-medium text-u-text2 transition hover:border-u-border-strong hover:text-u-text";

/** The Outreach tab's quieter second row: logging what happened, beneath the ways to reach out. */
export function LogContactRow({
  onLog,
  onAddNote,
}: {
  onLog: (kind: LoggedKind) => void;
  /** Offers Add note after the logs, for a screen whose notes are written here. */
  onAddNote?: () => void;
}) {
  return (
    <div role="group" aria-label="Log" className="flex flex-wrap items-center gap-1">
      <span aria-hidden="true" className="me-1 text-note text-u-text3">
        Log
      </span>
      {KINDS.map(({ kind, label, action }) => (
        <button
          key={kind}
          type="button"
          aria-label={action}
          onClick={() => onLog(kind)}
          className={LOG_CHIP}
        >
          {kind === "linkedin" || kind === "whatsapp" ? (
            <NoteKindMark kind={kind} size={12} />
          ) : (
            <Icon d={kind === "call" ? ICONS.phoneCall : ICONS.video} size={12} />
          )}
          {label}
        </button>
      ))}
      {onAddNote && (
        <button type="button" onClick={onAddNote} className={LOG_CHIP}>
          <Icon d={ICONS.pencil} size={12} />
          Add note
        </button>
      )}
    </div>
  );
}
