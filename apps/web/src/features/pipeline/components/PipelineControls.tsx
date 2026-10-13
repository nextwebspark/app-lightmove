import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { NextAction } from "../api/types";
import { dueLabelOf, dueToneOf, type DueTone } from "../lib/pipelineView";

const DUE_TONE_CLASS: Record<DueTone, string> = {
  overdue: "text-u-offlimits",
  today: "text-u-signal",
  later: "text-u-text3",
};

/** The next action and when it falls due — red once its day has passed, amber on the day. */
export function NextActionText({ nextAction, className }: { nextAction: NextAction | null; className?: string }) {
  if (!nextAction) return <span className={cn("text-u-text3", className)}>No next action</span>;
  return (
    <span className={cn("flex min-w-0 flex-col", className)}>
      <span className="truncate text-u-text2">{nextAction.text}</span>
      {nextAction.dueAt && (
        <span
          data-tone={dueToneOf(nextAction.dueAt)}
          className={cn(
            "inline-flex items-center gap-1 font-mono text-meta font-semibold",
            DUE_TONE_CLASS[dueToneOf(nextAction.dueAt)],
          )}
        >
          <Icon d={ICONS.clock} size={11} />
          {dueLabelOf(nextAction.dueAt)}
        </span>
      )}
    </span>
  );
}

/** Set, change or clear someone's next action in place. A blank text clears it. */
export function NextActionEditor({
  nextAction,
  saving,
  onSave,
  onCancel,
}: {
  nextAction: NextAction | null;
  saving: boolean;
  onSave: (text: string | null, dueAt: string | null) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(nextAction?.text ?? "");
  const [due, setDue] = useState(toLocalInput(nextAction?.dueAt ?? null));

  return (
    <form
      className="flex flex-col gap-1.5"
      onClick={(event) => event.stopPropagation()}
      onSubmit={(event) => {
        event.preventDefault();
        const trimmed = text.trim();
        onSave(trimmed || null, trimmed && due ? new Date(due).toISOString() : null);
      }}
    >
      <Input
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => event.key === "Escape" && onCancel()}
        placeholder="e.g. Book introductory call"
        aria-label="Next action"
        maxLength={200}
        autoFocus
        className="py-1 text-note"
      />
      <Input
        type="datetime-local"
        value={due}
        onChange={(event) => setDue(event.target.value)}
        aria-label="Due"
        className="py-1 text-note"
      />
      <span className="flex gap-1.5">
        <Button type="submit" size="xs" loading={saving}>
          Save
        </Button>
        {nextAction && (
          <Button type="button" variant="secondary" size="xs" disabled={saving} onClick={() => onSave(null, null)}>
            Clear
          </Button>
        )}
        <Button type="button" variant="ghost" size="xs" onClick={onCancel}>
          Cancel
        </Button>
      </span>
    </form>
  );
}

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

function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const moment = new Date(iso);
  if (Number.isNaN(moment.getTime())) return "";
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${moment.getFullYear()}-${pad(moment.getMonth() + 1)}-${pad(moment.getDate())}T${pad(moment.getHours())}:${pad(moment.getMinutes())}`;
}
