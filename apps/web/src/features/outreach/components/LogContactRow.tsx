import { Icon, ICONS } from "../../../components/layout/Icon";
import { NoteKindMark } from "../../candidates/components/NoteKindMark";

type LoggedKind = "call" | "meeting" | "linkedin";

const KINDS: readonly { kind: LoggedKind; label: string }[] = [
  { kind: "call", label: "Call" },
  { kind: "meeting", label: "Meeting" },
  { kind: "linkedin", label: "LinkedIn" },
];

/** The Outreach tab's quieter second row: logging what happened, beneath the ways to reach out. */
export function LogContactRow({ onLog }: { onLog: (kind: LoggedKind) => void }) {
  return (
    <div role="group" aria-label="Log" className="flex flex-wrap items-center gap-1">
      <span aria-hidden="true" className="me-1 text-note text-u-text3">
        Log
      </span>
      {KINDS.map(({ kind, label }) => (
        <button
          key={kind}
          type="button"
          aria-label={kind === "linkedin" ? "Log LinkedIn" : `Log ${kind}`}
          onClick={() => onLog(kind)}
          className="inline-flex items-center gap-1.5 rounded-full border border-u-border bg-u-canvas px-2.5 py-0.5 text-note font-medium text-u-text2 transition hover:border-u-border-strong hover:text-u-text"
        >
          {kind === "linkedin" ? (
            <NoteKindMark kind="linkedin" size={12} />
          ) : (
            <Icon d={kind === "call" ? ICONS.phoneCall : ICONS.video} size={12} />
          )}
          {label}
        </button>
      ))}
    </div>
  );
}
